# Pocketflow #Week1 : Genesis — The Core Idea & Multiplatform Architecture

I’ve decided to build something pretty ambitious: **Pocketflow**. 

If you’ve spent any time playing with generative AI, you know that the coolest results come from chaining models together—like feeding an AI-generated image into a video generator, adding a synthesized voice track, and exporting a final clip. On desktop, tools like ComfyUI make this a breeze. But on mobile? There's basically nothing. 

My goal is to bring that infinite, node-based workflow editor straight to our phones. 

To build this natively on both iOS and Android without having to maintain two separate codebases, **I will be going with Compose Multiplatform**. 

But before writing the first line of code, I spent this week doing a sanity check: *Can Compose Multiplatform actually handle this?* Here is the plan and the features I figured out that will make this work:

---

## 🎨 1. Navigating the Infinite Canvas
Honestly, I was terrified of having to build the drag-and-drop node graph UI twice. But I spent the week reading up on Compose Multiplatform's `Canvas` API and gesture detectors like `detectTransformGestures` for handling panning and pinch-to-zoom. By connecting the dots between those APIs and drawing Bézier curves between node coordinates, I realized I can build the entire node-editor interface once in shared code and have it render natively on both iOS and Android. This solves a massive UI fragmentation issue early on.

## 💾 2. Tackling the Heavy File Problem
AI generation produces heavy files (MP4s, PNGs, MP3s). The default cache directories on mobile are temporary and get wiped on system restarts. I need a way to save these assets persistently. 

I figured out that I can abstract the local file system using Kotlin Multiplatform's `expect`/`actual` declarations. The shared logic will just call a simple save function, but compile-time actuals will route it to the persistent `NSDocumentDirectory` on iOS and `filesDir` on Android:

```kotlin
// In commonMain (Common Code)
expect object LocalStorage {
    fun saveMediaToTemp(bytes: ByteArray, extension: String): String
}

// In iosMain (iOS side of the bridge)
actual object LocalStorage {
    actual fun saveMediaToTemp(bytes: ByteArray, extension: String): String {
        // Grab iOS Document directory and write raw bytes...
    }
}
```

## 🔔 3. Bringing in Native SDKs (OneSignal & Share Sheets)
Since rendering videos via AI takes time, I want to trigger push notifications when the generation is done. However, push notifications require native setup. 

I figured out that I don't need a pure Multiplatform library for this. I can set up OneSignal natively inside the iOS Swift app and Android Kotlin app, and expose a tiny interface bridge to the shared module. When the AI rendering finishes, the shared Compose code will simply trigger that interface, launching a native notification or the system's sharing overlay.

---

## 🚀 The Next Step
The research is done and the architecture design is locked in. Next week, I'll be partnering with an **AI coding agent** inside my IDE to start vibecoding the layout and infinite panning math of the canvas. I'll act as the architect directing the vision, and let the agent handle the heavy typing. 

Let's see how fast we can turn these diagrams into a working prototype!
