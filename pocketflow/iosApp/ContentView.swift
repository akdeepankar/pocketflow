import Shared
import SwiftUI
import UIKit
import CoreText

// MARK: - KMP Compose Bridge

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

// MARK: - Content View

struct ContentView: View {
    @EnvironmentObject private var auth: AuthViewModel
    @AppStorage("onboarding_completed") private var onboardingCompleted: String = "false"

    var body: some View {
        Group {
            if onboardingCompleted != "true" {
                // Show KMP Compose to display Onboarding Screen first
                ComposeView()
                    .ignoresSafeArea()
            } else if auth.isAuthenticated {
                // Show the real KMP Compose homepage after sign-in
                ComposeView()
                    .ignoresSafeArea()
            } else {
                SignInView()
            }
        }
        .task { await auth.checkSession() }
    }
}

// MARK: - Sign In View

struct SignInView: View {
    @EnvironmentObject private var auth: AuthViewModel

    // Load pocketflow_logo from the compose-resources bundle
    private var pocketflowLogo: UIImage? {
        let dirs = [
            "compose-resources/composeResources/pocketflow.shared.generated.resources/drawable",
            "composeResources/pocketflow.shared.generated.resources/drawable"
        ]
        for dir in dirs {
            if let path = Bundle.main.path(forResource: "pocketflow_logo", ofType: "png", inDirectory: dir),
               let img = UIImage(contentsOfFile: path) {
                return img
            }
        }
        return nil
    }
    var body: some View {
        ZStack {
            Color(red: 0.98, green: 0.98, blue: 0.98).ignoresSafeArea()

            VStack(spacing: 0) {

                Spacer().frame(height: 56)

                // Logo / Title
                VStack(spacing: 10) {
                    if let logo = pocketflowLogo {
                        Image(uiImage: logo)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 72, height: 72)
                            .clipShape(RoundedRectangle(cornerRadius: 18))
                            .shadow(color: .black.opacity(0.08), radius: 8, x: 0, y: 4)
                    } else {
                        RoundedRectangle(cornerRadius: 18)
                            .fill(
                                LinearGradient(
                                    colors: [Color(red: 0.055, green: 0.647, blue: 0.914),
                                             Color(red: 0.012, green: 0.502, blue: 0.757)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 72, height: 72)
                            .overlay(
                                Text("PF")
                                    .font(.system(size: 26, weight: .bold))
                                    .foregroundColor(.white)
                            )
                    }

                    Spacer().frame(height: 6)

                    Text("pocketflow")
                        .font(.system(size: 32, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.118, green: 0.161, blue: 0.235))

                    Text("Sign in to continue")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                // Flexible spacer pushes everything below to the bottom
                Spacer()

                // Error banner
                if let error = auth.errorMessage {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundColor(.red)
                        Text(error)
                            .font(.footnote)
                            .foregroundColor(.red)
                            .multilineTextAlignment(.leading)
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                }

                // "Best for Personal Use" + Guest button
                VStack(spacing: 10) {
                    Text("Best for Personal Use")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(white: 0.6))
                        .tracking(0.3)

                    SignInButton(
                        title: "Continue as Guest",
                        icon: "person.fill",
                        tint: Color(red: 0.39, green: 0.45, blue: 0.55)
                    ) {
                        auth.loginAsGuest()
                    }
                    .disabled(auth.isLoading)
                }
                .padding(.horizontal, 24)

                // Divider
                HStack {
                    Rectangle().fill(Color(white: 0.88)).frame(height: 1)
                    Text("OR")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(white: 0.6))
                        .padding(.horizontal, 12)
                    Rectangle().fill(Color(white: 0.88)).frame(height: 1)
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 20)

                // "Best for Teams Working Together" + Social buttons
                VStack(spacing: 10) {
                    Text("Best for Teams Working Together")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(white: 0.6))
                        .tracking(0.3)

                    SignInButton(
                        title: "Continue with Apple",
                        icon: "apple.logo",
                        tint: Color.primary
                    ) {
                        Task { await auth.signInWithApple() }
                    }
                    .disabled(auth.isLoading)
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 48)
            }
            .overlay {
                if auth.isLoading {
                    Color.black.opacity(0.3).ignoresSafeArea()
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(.white)
                }
            }
        }
    }
}

// MARK: - Sign In Button

struct SignInButton: View {
    let title: String
    let icon: String
    let tint: Color
    let action: () -> Void

    @State private var isPressed = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .medium))
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(tint.opacity(0.10))
            .foregroundColor(tint)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(tint.opacity(0.30), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .scaleEffect(isPressed ? 0.97 : 1.0)
        }
        .buttonStyle(.plain)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in withAnimation(.spring(response: 0.2)) { isPressed = true } }
                .onEnded { _ in withAnimation(.spring(response: 0.3)) { isPressed = false } }
        )
    }
}
