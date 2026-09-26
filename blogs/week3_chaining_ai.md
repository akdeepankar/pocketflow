# Pocketflow Week 3: Chaining the Canvas, Auth, and AI Pipelines

This week was all about brains and layout structure. With the infinite canvas successfully rendering in Compose Multiplatform, our focus turned to two critical areas: how a single AI pipeline actually executes, and how we allow users to create and switch between multiple independent canvas workspaces. 

If you have a Text Prompt node connected to an Image Generator, which in turn feeds into an Image-to-Video node, the engine needs to execute them in order. 

We also integrated a secure authentication system using Android's native Credential Manager for Google Sign-In, solving the common Play Store release key mismatch issues by implementing a double client-id structure.

---

## Managing Multiple Canvases

Before executing an AI pipeline, we needed a way to let users create and organize different project workspaces. We designed a multi-canvas system where each canvas is represented as a `Workflow` model containing its own unique ID, name, pastel card color, and a list of nodes and connection edges.

The active canvas is managed by the `WorkflowController`. When the user is on the dashboard, clicking on a card sets the active workspace ID, causing the infinite canvas editor to reload its nodes and edges:

```kotlin
// Select active canvas workspace
fun selectActiveWorkflow(workflowId: String) {
    _activeWorkflow.value = _workflows.value.find { it.id == workflowId }
}
```

```
[ Canvas Dashboard ] ──► Tap Canvas Card ──► selectActiveWorkflow(id)
                                                  │
                                                  ▼
[ Infinite Canvas Editor ] ◄── Load Nodes/Edges ◄─┘
```

This architecture allows users to easily jump from an AI Video creation canvas to a Text-to-Speech prototyping canvas without losing any layout state.

---

## Topological Sorting

To run a workflow, we first need to determine the execution order. If Node B depends on Node A, Node A must finish generating first. 

Using an in-degree topological sort, the engine traverses the graph edges, calculates dependencies (how many inputs are waiting for data), and builds an ordered list of tasks:

```kotlin
// Resolve dependent nodes sequentially
while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    order.add(current)
    
    // Decrease dependency count for all downstream nodes
    adjacencyList[current.id]?.forEach { dependentId ->
        inDegree[dependentId] = (inDegree[dependentId] ?: 0) - 1
        if (inDegree[dependentId] == 0) {
            queue.add(nodes.first { it.id == dependentId })
        }
    }
}
```

```
[ Text Input Node ] ──► [ Image Gen Node ] ──► [ Video Gen Node ]
  (Ready to Run)         (In-degree = 1)        (In-degree = 1)
```

---

## Running Asynchronous Tasks

Once the execution order is resolved, we run the nodes sequentially. For example, when the engine reaches the **Image-to-Video** node, we use **Runway for video generations** to generate high-fidelity AI videos. The engine extracts the input image URL and prompt text, and calls the Runway API using a suspend function so it doesn't freeze the app:

```kotlin
suspend fun executeNode(node: WorkflowNode, inputs: Map<String, String>): String {
    return when (node.type) {
        NodeType.IMAGE_TO_VIDEO -> runwayService.generateVideo(inputs["image"]!!, node.params["prompt"] ?: "")
        NodeType.TEXT_TO_SPEECH -> ttsService.synthesizeSpeech(inputs["text"] ?: "", voice = "Maya")
        else -> ""
    }
}
```

---

## Google Sign-In Setup

To execute cloud workflows, users must be logged in. We integrated Android's new Credential Manager API for Google Sign-In. 

To prevent failures in closed testing, we configured the secure OAuth layout:
1. Passed the Web Client ID to `.setServerClientId(...)` in the Kotlin code.
2. Created a separate Android Client ID in Google Cloud Console containing the package name and the Google Play App Signing SHA-1 fingerprint. 

This dual-layer config ensures the Google Sign-In bottom sheet opens seamlessly on both local debug and Play Store closed testing builds.

```
[ App Launch Sign-In Button ]
             │
             ▼
[ Android Credential Manager ] ──► Validates Signing Key (SHA-1)
             │
             ▼ (Success)
[ Google Identity Provider ]   ──► Returns ID Token
             │
             ▼
[ Supabase Auth Server ]       ──► Authenticates User Session
```

---

## iOS Sign in with Apple Setup

Alongside Google Sign-In on Android, we configured **Sign in with Apple** on the iOS side to provide a native authentication flow using the `AuthenticationServices` framework.

To handle this in our Kotlin Multiplatform architecture:
1. We defined a common `AuthBridge` interface to trigger native sign-in flows.
2. In Xcode, we added the **Sign in with Apple** capability to the iOS app bundle to authorize credentials.
3. We implemented the native callback using `ASAuthorizationControllerDelegate` to retrieve the secure identity token and authorization code.
4. The retrieved credentials are then passed back to our Supabase client to authenticate the user session.

```swift
import AuthenticationServices

// Native iOS Sign in with Apple Handler
class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate {
    private let onResult: (String?, String?) -> Void // (idToken, authCode)

    func performSignIn() {
        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
            let idToken = appleIDCredential.identityToken.flatMap { String(data: $0, encoding: .utf8) }
            let authCode = appleIDCredential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            onResult(idToken, nil)
        }
    }
}
```

```
[ Continue with Apple Button ]
             │
             ▼
[ iOS AuthenticationServices ] ──► Requests Apple ID Credentials
             │
             ▼ (Success)
[ Apple Identity Provider ]    ──► Returns Identity Token & Auth Code
             │
             ▼
[ Supabase Auth Server ]       ──► Authenticates User Session via OAuth
```

---

## The Next Step

Now that we can manage multiple canvases, execute complex chains of nodes in order, and sign in users securely, our next focus is handling data persistence and background completion. Next week, we will dive into Memory Lock and Push Alerts!\n