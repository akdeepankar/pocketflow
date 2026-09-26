import AuthenticationServices
import Foundation
import Shared
import SwiftUI

struct AppUser {
    let name: String
    let email: String
}

// MARK: - OAuth Session Manager
// Bypasses WebAuthComponent's ephemeral session (which breaks Apple Sign In)
// by managing ASWebAuthenticationSession ourselves.
@MainActor
final class OAuthSession: NSObject, ASWebAuthenticationPresentationContextProviding, ASAuthorizationControllerPresentationContextProviding {

    static let shared = OAuthSession()
    private var _session: ASWebAuthenticationSession?

    func open(url: URL, callbackScheme: String) async throws -> URL {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<URL, Error>) in
            let session = ASWebAuthenticationSession(
                url: url,
                callbackURLScheme: callbackScheme
            ) { callbackURL, error in
                if let error = error as? ASWebAuthenticationSessionError,
                   error.code == .canceledLogin {
                    continuation.resume(throwing: error)
                } else if let error = error {
                    continuation.resume(throwing: error)
                } else if let callbackURL = callbackURL {
                    print("[Auth] ✅ Callback URL: \(callbackURL.absoluteString)")
                    continuation.resume(returning: callbackURL)
                } else {
                    continuation.resume(throwing: URLError(.badServerResponse))
                }
            }
            // ✅ false = allows shared cookies (required for Apple Sign In)
            session.prefersEphemeralWebBrowserSession = false
            session.presentationContextProvider = self
            session.start()
            self._session = session // retain to prevent dealloc
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}

// MARK: - Apple Sign In Delegate Helper
class AppleSignInDelegate: NSObject, ASAuthorizationControllerDelegate {
    var continuation: CheckedContinuation<String, Error>?
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
           let identityTokenData = appleIDCredential.identityToken,
           let identityToken = String(data: identityTokenData, encoding: .utf8) {
            continuation?.resume(returning: identityToken)
        } else {
            continuation?.resume(throwing: NSError(domain: "AppleSignIn", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to extract identity token"]))
        }
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
    }
}

// MARK: - Auth ViewModel
@MainActor
class AuthViewModel: ObservableObject {
    @Published var isAuthenticated = false
    @Published var currentUser: AppUser? = nil
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    @Published var pingResult: String? = nil

    private let callbackScheme = "supabase-callback-pocketflow"
    private let endpoint = "https://rbdwfyavuiltcqqlnwml.supabase.co"
    private let apiKey   = "sb_publishable_pIYlY_BPRdeLaD7Ig0wSBQ_o1DHFxpV"

    init() {
        // Restore session state from UserDefaults on cold launch — no network call needed.
        let userId = UserDefaults.standard.string(forKey: "appwrite_user_id") ?? ""
        let userName = UserDefaults.standard.string(forKey: "user_name") ?? ""
        let userEmail = UserDefaults.standard.string(forKey: "user_email") ?? ""
        let isGuest = UserDefaults.standard.string(forKey: "is_guest") ?? ""
        
        if isGuest == "true" {
            isAuthenticated = true
            print("[Auth] Cold launch — restored guest session")
            startSignOutPolling()
        } else if !userId.isEmpty {
            isAuthenticated = true
            currentUser = AppUser(name: userName, email: userEmail)
            print("[Auth] Cold launch — restored session for userId=\(userId)")
            OneSignalManager.shared.login(userId: userId)
            // Restart background timers so JWT stays fresh and sign-out polling works
            startSignOutPolling()
        } else {
            print("[Auth] Cold launch — no stored session, showing login")
        }
    }

    // MARK: - Check Session
    func checkSession() async {
        let storedUserId = UserDefaults.standard.string(forKey: "appwrite_user_id") ?? ""
        let jwt = UserDefaults.standard.string(forKey: "appwrite_jwt") ?? ""
        guard !storedUserId.isEmpty, !jwt.isEmpty else {
            print("[Auth] No stored credentials — skipping checkSession network call")
            isAuthenticated = false
            return
        }

        isLoading = true
        defer { isLoading = false }
        
        if let user = await fetchAccount(jwt: jwt) {
            currentUser = AppUser(name: user.name, email: user.email)
            isAuthenticated = true
            UserDefaults.standard.set(user.name, forKey: "user_name")
            UserDefaults.standard.set(user.email, forKey: "user_email")
            UserDefaults.standard.set(user.id, forKey: "appwrite_user_id")
            UserDefaults.standard.removeObject(forKey: "sign_out_requested")
            OneSignalManager.shared.login(userId: user.id)
            print("[Auth] ✅ Session valid — user: \(user.name) (\(user.email))")
            startSignOutPolling()
            SharedAuthViewModel.shared.notifyNativeAuthSuccess()
        } else {
            print("[Auth] ❌ checkSession failed")
            isAuthenticated = false
            currentUser = nil
            errorMessage = "Session check failed"
        }
    }

    // MARK: - Sign-out polling
    private var signOutTimer: Timer?

    private func startSignOutPolling() {
        signOutTimer?.invalidate()
        signOutTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self else { return }
            if UserDefaults.standard.string(forKey: "sign_out_requested") == "true" {
                UserDefaults.standard.removeObject(forKey: "sign_out_requested")
                Task { @MainActor in
                    await self.signOut()
                }
            }
        }
    }

    private func stopSignOutPolling() {
        signOutTimer?.invalidate()
        signOutTimer = nil
    }

    // MARK: - Guest Login
    func loginAsGuest() {
        UserDefaults.standard.set("true", forKey: "is_guest")
        UserDefaults.standard.synchronize()
        isAuthenticated = true
        startSignOutPolling()
        print("[Auth] Logged in as guest")
    }

    func showLoginPage() {
        stopSignOutPolling()
        UserDefaults.standard.removeObject(forKey: "is_guest")
        UserDefaults.standard.removeObject(forKey: "sign_out_requested")
        UserDefaults.standard.synchronize()
        isAuthenticated = false
        currentUser = nil
        print("[Auth] showLoginPage → isAuthenticated = false (native SignInView)")
    }

    // MARK: - Google Sign In
    func signInWithGoogle() async {
        await signInWithOAuth(provider: "google")
    }

    // MARK: - Apple Sign In
    func signInWithApple() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        
        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        
        let controller = ASAuthorizationController(authorizationRequests: [request])
        let delegate = AppleSignInDelegate()
        controller.delegate = delegate
        controller.presentationContextProvider = OAuthSession.shared
        
        do {
            let idToken = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                delegate.continuation = continuation
                controller.performRequests()
            }
            
            print("[Auth] Received Apple ID Token, exchanging with Supabase...")
            await signInWithIdToken(provider: "apple", idToken: idToken)
            
        } catch {
            print("[Auth] Apple Sign-In native error: \(error)")
            errorMessage = "Apple Sign-In failed: \(error.localizedDescription)"
        }
    }

    private func signInWithIdToken(provider: String, idToken: String) async {
        guard let url = URL(string: "\(endpoint)/auth/v1/token?grant_type=id_token") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey,             forHTTPHeaderField: "apikey")
        req.httpBody = try? JSONSerialization.data(withJSONObject: [
            "provider": provider,
            "id_token": idToken
        ])
        
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                errorMessage = "Invalid response from server"
                return
            }
            if http.statusCode == 200 || http.statusCode == 201 {
                let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
                let accessToken = json?["access_token"] as? String ?? ""
                let userObj = json?["user"] as? [String: Any]
                let id = userObj?["id"] as? String ?? ""
                let email = userObj?["email"] as? String ?? ""
                let meta = userObj?["user_metadata"] as? [String: Any]
                let name = meta?["full_name"] as? String ?? meta?["name"] as? String ?? email.components(separatedBy: "@").first ?? "User"
                
                guard !accessToken.isEmpty else {
                    errorMessage = "Missing access token"
                    return
                }
                
                UserDefaults.standard.set(accessToken, forKey: "appwrite_jwt")
                UserDefaults.standard.set(id, forKey: "appwrite_user_id")
                UserDefaults.standard.set(name, forKey: "user_name")
                UserDefaults.standard.set(email, forKey: "user_email")
                UserDefaults.standard.removeObject(forKey: "is_guest")
                UserDefaults.standard.synchronize()

                currentUser = AppUser(name: name, email: email)
                isAuthenticated = true
                OneSignalManager.shared.login(userId: id)
                
                print("[Auth] ✅ Native Apple login successful for user: \(name)")
                startSignOutPolling()
                SharedAuthViewModel.shared.notifyNativeAuthSuccess()
            } else {
                let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
                errorMessage = json?["error_description"] as? String ?? json?["msg"] as? String ?? "Token exchange failed (\(http.statusCode))"
            }
        } catch {
            errorMessage = "Server connection error: \(error.localizedDescription)"
        }
    }

    // MARK: - OAuth Flow
    private func signInWithOAuth(provider: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let successURL = "\(callbackScheme)://auth/oauth2/success"
        let authURLString = "\(endpoint)/auth/v1/authorize?provider=\(provider)&redirect_to=\(successURL)"
        
        guard let url = URL(string: authURLString) else {
            errorMessage = "Failed to build OAuth URL"
            return
        }

        print("[Auth] Opening OAuth URL: \(url)")

        do {
            let callbackURL = try await OAuthSession.shared.open(url: url, callbackScheme: callbackScheme)
            
            // Extract access_token from fragment
            let fragment = callbackURL.fragment ?? ""
            var params = [String: String]()
            for item in fragment.components(separatedBy: "&") {
                let parts = item.components(separatedBy: "=")
                if parts.count == 2 {
                    if let key = parts[0].removingPercentEncoding,
                       let val = parts[1].removingPercentEncoding {
                        params[key] = val
                    }
                }
            }

            guard let accessToken = params["access_token"] else {
                errorMessage = "Sign-in failed: missing access_token in callback URL"
                return
            }

            print("[Auth] Received access token, fetching user details...")

            // Fetch user info from Supabase
            if let user = await fetchAccount(jwt: accessToken) {
                UserDefaults.standard.set(accessToken, forKey: "appwrite_jwt")
                UserDefaults.standard.set(user.id, forKey: "appwrite_user_id")
                UserDefaults.standard.set(user.name, forKey: "user_name")
                UserDefaults.standard.set(user.email, forKey: "user_email")
                UserDefaults.standard.removeObject(forKey: "is_guest")
                UserDefaults.standard.synchronize()

                currentUser = AppUser(name: user.name, email: user.email)
                isAuthenticated = true
                OneSignalManager.shared.login(userId: user.id)
                
                print("[Auth] ✅ OAuth login successful for user: \(user.name)")
                startSignOutPolling()
                SharedAuthViewModel.shared.notifyNativeAuthSuccess()
            } else {
                errorMessage = "Failed to retrieve user details"
            }

        } catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin {
            print("[Auth] OAuth cancelled by user")
        } catch {
            errorMessage = "Sign-in failed: \(error.localizedDescription)"
            print("[Auth] OAuth Error: \(error)")
        }
    }

    // MARK: - Sign Out
    func signOut() async {
        stopSignOutPolling()
        OneSignalManager.shared.logout()
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let wasGuest = UserDefaults.standard.string(forKey: "is_guest") == "true"
        UserDefaults.standard.removeObject(forKey: "is_guest")

        guard !wasGuest else {
            isAuthenticated = false
            currentUser = nil
            print("[Auth] Guest session signed out")
            return
        }

        let jwt = UserDefaults.standard.string(forKey: "appwrite_jwt") ?? ""
        if !jwt.isEmpty {
            _ = await deleteCurrentSession(jwt: jwt)
        }

        UserDefaults.standard.removeObject(forKey: "user_name")
        UserDefaults.standard.removeObject(forKey: "user_email")
        UserDefaults.standard.removeObject(forKey: "appwrite_user_id")
        UserDefaults.standard.removeObject(forKey: "appwrite_jwt")
        UserDefaults.standard.synchronize()
        
        isAuthenticated = false
        currentUser = nil
        pingResult = nil
        print("[Auth] Supabase session signed out")
    }

    // MARK: - API Helpers
    private func fetchAccount(jwt: String) async -> (id: String, name: String, email: String)? {
        guard let url = URL(string: "\(endpoint)/auth/v1/user") else { return nil }
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(apiKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let id = json?["id"] as? String ?? ""
            let email = json?["email"] as? String ?? ""
            let meta = json?["user_metadata"] as? [String: Any]
            let name = meta?["full_name"] as? String ?? meta?["name"] as? String ?? email.components(separatedBy: "@").first ?? "User"
            return (id, name, email)
        } catch {
            return nil
        }
    }

    private func deleteCurrentSession(jwt: String) async -> Bool {
        guard let url = URL(string: "\(endpoint)/auth/v1/logout") else { return false }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(apiKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")

        do {
            let (_, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse else { return false }
            return http.statusCode == 204 || http.statusCode == 200
        } catch {
            return false
        }
    }
}
