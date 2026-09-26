# Pocketflow Week 6: Launch Sequence, Paywalls and Instant Credit Sync

This is it. The final week of our building in public timeline. Pocketflow is going live. 

Over the last 6 weeks, we’ve taken Pocketflow from a simple design idea to a native, gesture-controlled collaborative node editor that runs AI image, video, and audio pipelines on mobile. 

This week was all about connecting the final pipes: monetization, optimized credit updates, and release.

---

## Monetization and Subscriptions

Running AI models isn't free, so I needed a fair monetization model. I went with a dual approach: a weekly/yearly subscription coupled with consumable **Virtual Credits** that get deducted based on the complexity of each running node.

We integrated **RevenueCat's UI library** to show beautiful paywalls instantly, and customized the credit updating loop:

```kotlin
// Render paywall screen dynamically
Paywall(options = PaywallOptions.Builder(dismissHandler = onDismiss).build())
```

---

## Mapping Node Credit Rates

To manage credit usage, we map a dynamic rates table. Nodes consume different amounts of credits depending on processing costs. For instance, generating an AI video using Runway is significantly more computationally expensive than creating a simple static image with Google Gemini. 

Therefore, the Runway Image-to-Video node consumes 5 credits per run while the Gemini Image Generation node consumes just 1 credit:

```kotlin
// Map custom credit consumption rates for AI models
val defaultRates = mapOf(
    NodeType.IMAGE_GENERATION to 1,  // Gemini
    NodeType.IMAGE_TO_VIDEO to 5,    // Runway Gen-3
    NodeType.TEXT_TO_SPEECH to 2
)
```

---

## Fast Credits Refresh

A common issue with virtual currencies in native SDKs is propagation latency. When a user buys a credit package, they want to see their balance increase immediately. 

We optimized the sync engine by introducing an **aggressive polling cycle** that queries the RevenueCat backend every **500ms** (up to 10 times) immediately following a successful purchase transaction. This eliminated the previous 1.2s/4s delay, reflecting credits instantly!

```kotlin
// Poll rapidly to reflect credits instantly
for (i in 1..10) {
    delay(500)
    fetchVirtualCurrencies(forceRefresh = true)
}
```

```
[ User Completes Purchase ]
            │
            ▼
[ Invalidate Local Currencies Cache ]
            │
            ▼
[ Loop: Poll every 500ms ] ──► Fetch fresh balance from RevenueCat ──► Update UI
```

---

## Pocketflow is Live!

What a ride. What started as an abstract research idea is now a production-ready application. 

By combining **Compose Multiplatform** with an **AI coding agent**, I was able to build, test, and polish a fully functioning iOS & Android app inside 6 weeks. 

Pocketflow is officially live on the iOS App Store and Android Play Store. Down the line, I'll be adding more nodes and automation layers. Thank you so much for following along! Let me know if you download the app and build your first generation!\n