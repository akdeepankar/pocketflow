import Foundation
import SwiftUI
import Shared
import AuthenticationServices

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


// ─────────────────────────────────────────────────────────────────────────────
// AuthViewModel
//
// Responsibilities:
//   1. On cold launch  → read stored credentials from UserDefaults; set
//      isLoggedIn = true immediately (no network call) so the home screen
//      appears without delay.
//   2. Login / Sign-up → call Supabase REST API (GoTrue), persist credentials, then
//      set isLoggedIn = true so RootView transitions to the home screen.
//   3. Sign-out        → delete the Supabase session, wipe UserDefaults,
//      set isLoggedIn = false so RootView transitions back to LoginView.
//
// UserDefaults keys written here (read by KMP LocalStorage.ios.kt):
//   • supabase_jwt / appwrite_jwt         – JWT / access token for Supabase API calls
//   • supabase_session_id / appwrite_session_id – Session refresh token
//   • supabase_user_id / appwrite_user_id – Supabase user UUID
//   • user_name                           – display name
//   • user_email                          – email address
//   • sign_out_requested                  – KMP sets this to "true" to trigger sign-out
//
// IMPORTANT: Always clear "sign_out_requested" before writing other keys.
// RootView listens to UserDefaults.didChangeNotification and calls signOut()
// when it sees "true". Writing JWT/session after login can trigger this
// listener if a stale "true" is left in UserDefaults.
// ─────────────────────────────────────────────────────────────────────────────

@MainActor
class AuthViewModel: ObservableObject {

    // ── Published state (drives RootView) ────────────────────────────────────
    @Published var isLoggedIn:    Bool    = false
    @Published var isLoading:     Bool    = false
    @Published var errorMessage:  String? = nil

    // Polling fallback: watches the sign_out_requested flag written by KMP.
    // More reliable than the UserDefaults notification in RootView, which can
    // be missed when KMP writes many keys in quick succession.
    private var signOutTimer: Timer?

    // ── Supabase config ───────────────────────────────────────────────────────
    private let endpoint = "https://rbdwfyavuiltcqqlnwml.supabase.co"
    private let apiKey   = "sb_publishable_pIYlY_BPRdeLaD7Ig0wSBQ_o1DHFxpV"

    // ── Storage ───────────────────────────────────────────────────────────────
    private let defaults = UserDefaults.standard

    // URLSession that persists cookies across requests.
    // Required so the session cookie from createSession() is automatically
    // sent in the subsequent createJWT() call.
    private let urlSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies   = true
        return URLSession(configuration: config)
    }()

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Init (cold launch)
    // ─────────────────────────────────────────────────────────────────────────

    init() {
        let jwt       = defaults.string(forKey: "supabase_jwt")        ?? defaults.string(forKey: "appwrite_jwt")        ?? ""
        let sessionId = defaults.string(forKey: "supabase_session_id") ?? defaults.string(forKey: "appwrite_session_id") ?? ""
        let userId    = defaults.string(forKey: "supabase_user_id")    ?? defaults.string(forKey: "appwrite_user_id")    ?? ""
        let isGuest   = defaults.string(forKey: "is_guest")            ?? ""

        if !jwt.isEmpty || !sessionId.isEmpty || !userId.isEmpty {
            isLoggedIn = true
        } else {
            isLoggedIn = false
            defaults.set("", forKey: "is_guest")
            defaults.synchronize()
        }



        print("[Auth] ===== COLD LAUNCH =====")
        print("[Auth] jwt      = '\(jwt.isEmpty      ? "<empty>" : String(jwt.prefix(20)))...'")
        print("[Auth] session  = '\(sessionId.isEmpty ? "<empty>" : String(sessionId.prefix(20)))...'")
        print("[Auth] userId   = '\(userId.isEmpty    ? "<empty>" : userId)'")
        print("[Auth] isGuest  = \(isGuest)")
        print("[Auth] isLoggedIn = \(isLoggedIn)")

        // Clear any stale sign-out flag so the UserDefaults listener in
        // RootView doesn't fire immediately on launch.
        clearSignOutFlag()

        if isLoggedIn {
            if !userId.isEmpty {
                OneSignalManager.shared.login(userId: userId)
            }
            startSignOutPolling()
            Task { @MainActor in
                await self.checkSessionStatus()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Login (existing account)
    // ─────────────────────────────────────────────────────────────────────────

    func login(email: String, password: String) async {
        isLoading    = true
        errorMessage = nil
        defer { isLoading = false }

        // Clear stale sign-out flag BEFORE any UserDefaults writes.
        clearSignOutFlag()

        // Step 1: Create Supabase session (required — everything else is best-effort).
        guard let sessionId = await createSession(email: email, password: password) else { return }

        // Step 2: Persist session info.
        defaults.set(sessionId, forKey: "supabase_session_id")
        defaults.set(sessionId, forKey: "appwrite_session_id")
        defaults.set(email,     forKey: "user_email")
        defaults.synchronize()

        // Step 3: Fetch JWT + account details inline so they are available
        // before the home screen renders.
        await fetchAndPersistJWTAndAccount(sessionId: sessionId)

        // Step 4: Navigate to home screen.
        isLoggedIn = true
        startSignOutPolling()
        // Keep the shared Compose layer in sync (triggers cloud sync).
        SharedAuthViewModel.shared.notifyNativeAuthSuccess()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Sign Up (new account)
    // ─────────────────────────────────────────────────────────────────────────

    func signUp(name: String, email: String, password: String) async {
        isLoading    = true
        errorMessage = nil
        defer { isLoading = false }

        // Clear stale sign-out flag BEFORE any UserDefaults writes.
        clearSignOutFlag()

        // Step 1: Create Supabase account.
        guard await createAccount(name: name, email: email, password: password) else { return }

        // Step 2: Create session.
        guard let sessionId = await createSession(email: email, password: password) else { return }

        // Step 3: Persist session + name from the sign-up form immediately
        // so KMP layer has a name before fetchAccount completes.
        defaults.set(sessionId, forKey: "supabase_session_id")
        defaults.set(sessionId, forKey: "appwrite_session_id")
        defaults.set(email,     forKey: "user_email")
        defaults.set(name,      forKey: "user_name")
        defaults.synchronize()

        // Step 4: Fetch JWT + account details inline (same reason as login).
        await fetchAndPersistJWTAndAccount(sessionId: sessionId)

        // Step 5: Navigate to home screen.
        isLoggedIn = true
        startSignOutPolling()
        // Keep the shared Compose layer in sync (triggers cloud sync).
        SharedAuthViewModel.shared.notifyNativeAuthSuccess()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Sign Out
    // ─────────────────────────────────────────────────────────────────────────

    func signOut() async {
        isLoading = true
        defer { isLoading = false }

        stopSignOutPolling()

        // Best-effort: delete the session on the server.
        let jwt = defaults.string(forKey: "supabase_jwt") ?? defaults.string(forKey: "appwrite_jwt") ?? ""
        if !jwt.isEmpty {
            _ = await deleteCurrentSession(jwt: jwt)
        }

        // Wipe all stored credentials including guest flag.
        for key in ["supabase_jwt", "supabase_session_id", "supabase_user_id",
                    "appwrite_jwt", "appwrite_session_id", "appwrite_user_id",
                    "user_name", "user_email", "sign_out_requested", "is_guest"] {
            defaults.set("", forKey: key)
        }
        defaults.synchronize()

        OneSignalManager.shared.logout()
        isLoggedIn = false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Guest Login
    // ─────────────────────────────────────────────────────────────────────────

    func loginAsGuest() {
        clearSignOutFlag()
        defaults.set("true", forKey: "is_guest")
        defaults.synchronize()
        isLoggedIn = true
        startSignOutPolling()
        print("[Auth] Logged in as guest")
    }

    /// Flips the app straight to the native LoginView. Used by the shared guest
    /// "Sign In" sheet. Unlike signOut(), it does NOT delete the server session.
    func showLoginPage() {
        stopSignOutPolling()
        defaults.set("", forKey: "sign_out_requested")
        defaults.set("", forKey: "is_guest")
        defaults.set("", forKey: "user_name")
        defaults.set("", forKey: "user_email")
        defaults.synchronize()
        isLoggedIn = false
        print("[Auth] showLoginPage → isLoggedIn = false (native LoginView)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Social Sign In (Google / Apple)
    // ─────────────────────────────────────────────────────────────────────────

    func signInWithGoogle() async {
        // TODO: integrate OAuth2 / Google Sign-In SDK
        print("[Auth] Google sign-in not yet implemented on iOS")
        errorMessage = "Google sign-in coming soon."
    }

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
            let (data, response) = try await urlSession.data(for: req)
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
                
                defaults.set(accessToken, forKey: "supabase_jwt")
                defaults.set(accessToken, forKey: "appwrite_jwt")
                defaults.set(id, forKey: "supabase_user_id")
                defaults.set(id, forKey: "appwrite_user_id")
                defaults.set(name, forKey: "user_name")
                defaults.set(email, forKey: "user_email")
                defaults.removeObject(forKey: "is_guest")
                defaults.synchronize()

                OneSignalManager.shared.login(userId: id)
                isLoggedIn = true
                
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


    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Shared helper: fetch JWT + account and persist
    // ─────────────────────────────────────────────────────────────────────────

    /// Fetches a JWT using the session cookie, then fetches account details.
    /// Persists supabase_jwt, supabase_user_id, user_name to UserDefaults.
    private func fetchAndPersistJWTAndAccount(sessionId: String) async {
        // Always clear sign-out flag before writing to UserDefaults to prevent
        // the RootView listener from triggering a spurious sign-out.
        clearSignOutFlag()

        // Retry JWT creation up to 3 times with a short delay.
        // The session cookie may not be available immediately after createSession().
        var jwt: String? = nil
        for attempt in 1...3 {
            jwt = await createJWT()
            if jwt != nil { break }
            print("[Auth] JWT attempt \(attempt) failed — retrying in 1s...")
            try? await Task.sleep(nanoseconds: 1_000_000_000)
        }

        if let jwt = jwt {
            let (userId, fetchedName) = await fetchAccount(jwt: jwt) ?? ("", "")
            defaults.set(jwt, forKey: "supabase_jwt")
            defaults.set(jwt, forKey: "appwrite_jwt")
            if !userId.isEmpty {
                defaults.set(userId, forKey: "supabase_user_id")
                defaults.set(userId, forKey: "appwrite_user_id")
                OneSignalManager.shared.login(userId: userId)
            }
            if !fetchedName.isEmpty { defaults.set(fetchedName,  forKey: "user_name") }
            defaults.synchronize()
            print("[Auth] JWT persisted successfully")
        } else {
            print("[Auth] JWT creation failed after retries — storing empty JWT")
            defaults.set("", forKey: "supabase_jwt")
            defaults.set("", forKey: "appwrite_jwt")
            defaults.synchronize()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Private Supabase REST helpers
    // ─────────────────────────────────────────────────────────────────────────

    /// POST /auth/v1/token?grant_type=password → returns session token on success.
    private func createSession(email: String, password: String) async -> String? {
        guard let url = URL(string: "\(endpoint)/auth/v1/token?grant_type=password") else { return nil }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey,             forHTTPHeaderField: "apikey")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["email": email, "password": password])

        do {
            let (data, response) = try await urlSession.data(for: req)
            guard let http = response as? HTTPURLResponse else { return nil }
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]

            if http.statusCode == 200 || http.statusCode == 201 {
                if let user = json?["user"] as? [String: Any],
                   let uid = user["id"] as? String {
                    defaults.set(uid, forKey: "supabase_user_id")
                    defaults.set(uid, forKey: "appwrite_user_id")
                }
                if let accessToken = json?["access_token"] as? String {
                    defaults.set(accessToken, forKey: "supabase_jwt")
                    defaults.set(accessToken, forKey: "appwrite_jwt")
                }
                defaults.synchronize()
                return json?["access_token"] as? String
            }

            errorMessage = json?["error_description"] as? String ?? json?["msg"] as? String ?? "Login failed (\(http.statusCode))"
            return nil
        } catch {
            errorMessage = "Network error: \(error.localizedDescription)"
            return nil
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - JWT Expiration & Session Refresh
    // ─────────────────────────────────────────────────────────────────────────

    func isJwtExpired(token: String) -> Bool {
        guard !token.isEmpty else { return true }
        let parts = token.components(separatedBy: ".")
        guard parts.count >= 2 else { return true }
        var payload = parts[1]
        let remainder = payload.count % 4
        if remainder > 0 {
            payload += String(repeating: "=", count: 4 - remainder)
        }
        guard let data = Data(base64Encoded: payload, options: .ignoreUnknownCharacters),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let exp = json["exp"] as? TimeInterval else {
            return true
        }
        let now = Date().timeIntervalSince1970
        return exp < (now + 60) // Expired if less than 60s remaining
    }

    func refreshSession() async -> Bool {
        let refreshToken = defaults.string(forKey: "supabase_session_id") ?? defaults.string(forKey: "appwrite_session_id") ?? ""
        guard !refreshToken.isEmpty else { return false }
        guard let url = URL(string: "\(endpoint)/auth/v1/token?grant_type=refresh_token") else { return false }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey, forHTTPHeaderField: "apikey")
        req.httpBody = try? JSONSerialization.data(withJSONObject: [
            "refresh_token": refreshToken
        ])

        do {
            let (data, response) = try await urlSession.data(for: req)
            guard let http = response as? HTTPURLResponse, (http.statusCode == 200 || http.statusCode == 201) else {
                return false
            }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let accessToken = json["access_token"] as? String, !accessToken.isEmpty else {
                return false
            }
            let newRefreshToken = json["refresh_token"] as? String ?? refreshToken
            let userObj = json["user"] as? [String: Any]
            let id = userObj?["id"] as? String ?? defaults.string(forKey: "supabase_user_id") ?? ""
            let email = userObj?["email"] as? String ?? defaults.string(forKey: "user_email") ?? ""
            let meta = userObj?["user_metadata"] as? [String: Any]
            let name = meta?["full_name"] as? String ?? meta?["name"] as? String ?? defaults.string(forKey: "user_name") ?? ""

            defaults.set(accessToken, forKey: "supabase_jwt")
            defaults.set(accessToken, forKey: "appwrite_jwt")
            defaults.set(newRefreshToken, forKey: "supabase_session_id")
            defaults.set(newRefreshToken, forKey: "appwrite_session_id")
            if !id.isEmpty {
                defaults.set(id, forKey: "supabase_user_id")
                defaults.set(id, forKey: "appwrite_user_id")
            }
            if !name.isEmpty {
                defaults.set(name, forKey: "user_name")
            }
            if !email.isEmpty {
                defaults.set(email, forKey: "user_email")
            }
            defaults.synchronize()
            print("[Auth] ✅ Session refreshed successfully on iOS")
            return true
        } catch {
            print("[Auth] ❌ Session refresh failed on iOS: \(error)")
            return false
        }
    }

    func checkSessionStatus() async {
        let jwt = defaults.string(forKey: "supabase_jwt") ?? defaults.string(forKey: "appwrite_jwt") ?? ""
        if jwt.isEmpty || isJwtExpired(token: jwt) {
            print("[Auth] JWT is missing or expired. Attempting token refresh...")
            let refreshed = await refreshSession()
            if !refreshed {
                print("[Auth] ❌ Session could not be refreshed. Logging out...")
                await signOut()
                return
            }
        }

        // Test fetching account with current / refreshed token
        let currentJwt = defaults.string(forKey: "supabase_jwt") ?? defaults.string(forKey: "appwrite_jwt") ?? ""
        if let (userId, fetchedName) = await fetchAccount(jwt: currentJwt) {
            if !userId.isEmpty {
                defaults.set(userId, forKey: "supabase_user_id")
                defaults.set(userId, forKey: "appwrite_user_id")
            }
            if !fetchedName.isEmpty {
                defaults.set(fetchedName, forKey: "user_name")
            }
            defaults.synchronize()
        } else {
            // fetchAccount failed (likely 401 or invalid token), try refreshing once
            print("[Auth] fetchAccount failed with token, trying refreshSession...")
            let refreshed = await refreshSession()
            if refreshed {
                let newJwt = defaults.string(forKey: "supabase_jwt") ?? defaults.string(forKey: "appwrite_jwt") ?? ""
                if await fetchAccount(jwt: newJwt) == nil {
                    print("[Auth] ❌ fetchAccount still failed after refresh. Logging out...")
                    await signOut()
                }
            } else {
                print("[Auth] ❌ Session expired and refresh failed. Logging out...")
                await signOut()
            }
        }
    }

    /// Returns the cached JWT/access_token string.
    private func createJWT() async -> String? {
        return defaults.string(forKey: "supabase_jwt") ?? defaults.string(forKey: "appwrite_jwt")
    }

    /// GET /auth/v1/user → returns (userId, displayName) using the provided JWT.
    private func fetchAccount(jwt: String) async -> (String, String)? {
        guard let url = URL(string: "\(endpoint)/auth/v1/user") else { return nil }
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(apiKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await urlSession.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let json   = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let userId = json?["id"]  as? String ?? ""
            let meta   = json?["user_metadata"] as? [String: Any]
            let name   = meta?["name"] as? String ?? ""
            return (userId, name)
        } catch {
            return nil
        }
    }

    /// POST /auth/v1/signup → creates a new Supabase account. Returns true on success.
    private func createAccount(name: String, email: String, password: String) async -> Bool {
        guard let url = URL(string: "\(endpoint)/auth/v1/signup") else { return false }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey,             forHTTPHeaderField: "apikey")
        req.httpBody = try? JSONSerialization.data(withJSONObject: [
            "email":    email,
            "password": password,
            "options":  ["data": ["name": name]]
        ])

        do {
            let (data, response) = try await urlSession.data(for: req)
            guard let http = response as? HTTPURLResponse else { return false }
            if http.statusCode == 200 || http.statusCode == 201 { return true }
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            errorMessage = json?["msg"] as? String ?? json?["error_description"] as? String ?? "Sign up failed (\(http.statusCode))"
            return false
        } catch {
            errorMessage = "Network error: \(error.localizedDescription)"
            return false
        }
    }

    /// POST /auth/v1/logout → invalidates the active session on the server.
    private func deleteCurrentSession(jwt: String) async -> Bool {
        guard let url = URL(string: "\(endpoint)/auth/v1/logout") else { return false }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(apiKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")

        do {
            let (_, response) = try await urlSession.data(for: req)
            guard let http = response as? HTTPURLResponse else { return false }
            return http.statusCode == 204 || http.statusCode == 200
        } catch {
            return false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Utility
    // ─────────────────────────────────────────────────────────────────────────

    /// Clears the sign_out_requested flag so the RootView UserDefaults listener
    /// doesn't trigger a spurious sign-out during login/sign-up writes.
    private func clearSignOutFlag() {
        defaults.set("", forKey: "sign_out_requested")
        defaults.synchronize()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Sign-out polling (watches flag written by KMP)
    // ─────────────────────────────────────────────────────────────────────────

    /// Polls for the "sign_out_requested" flag that KMP sets to "true".
    /// Fallback in case the UserDefaults didChangeNotification is missed.
    private func startSignOutPolling() {
        stopSignOutPolling()
        signOutTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self else { return }
            if UserDefaults.standard.string(forKey: "sign_out_requested") == "true" {
                UserDefaults.standard.set("", forKey: "sign_out_requested")
                UserDefaults.standard.synchronize()
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
}
