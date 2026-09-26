# Pocketflow Weeks 5 & 6: Polish, Paywalls, Timeline Audits, and Going Live!

These final two weeks brought Pocketflow across the finish line—from UI refinement and activity auditing to native monetization and our official public release. 

Over the last 6 weeks, we’ve taken Pocketflow from an abstract design concept to a native, gesture-controlled collaborative node editor running AI image, video, and audio pipelines across iOS and Android.

---

## 1. Onboarding Screen Layout & UI Polish

Aesthetics and onboarding set the tone for the entire app experience. We overhauled the login screen user interface to make actions feel intuitive and cleanly structured, aligning buttons to the bottom base of the screen using Jetpack Compose `Arrangement.SpaceBetween`. We also added dedicated vector icons for Google Sign-In and Guest mode:

* **Google Sign-In**: A programmatic, vector-drawn representation of the official Google "G" logo.
* **Guest Mode**: A sleek user outline icon.

```kotlin
// Relocated and branded Google Sign-In button
OutlinedButton(onClick = { onGoogleSignInClick() }) {
    Icon(imageVector = GoogleIcon, contentDescription = "Google Icon", tint = Color.Unspecified)
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = "Continue with Google")
}
```

```
┌──────────────────────────────────────────────┐
│  Login Screen Layout                         │
│                                              │
│                 [ Logo & Title ]             │
│                                              │
│                                              │
│                  ( Spacer )                  │
│                                              │
│  [ [Icon] Continue as Guest ]                │
│  [OR]                                        │
│  [ [Icon] Continue with Google ]             │
└──────────────────────────────────────────────┘
```

---

## 2. Pastel Canvas Themes

To give users customization options when designing workflows, we introduced a 5-color color selector using soft, crayon-inspired pastel themes within the workflow options sheet:

```kotlin
val crayonColors = listOf("#FFFFFF", "#FFE5EC", "#FFF2CC", "#E2F0D9", "#E8F0FE")
```

---

## 3. Vertical Line Tracker (Timeline Audit Log)

Transparency in credit usage and user activity is essential. Instead of heavy card lists, we built a vertical line activity log. A thin vertical line runs down the left margin, with colored circular bullets placed directly on the line for each action. We utilized Compose's `IntrinsicSize.Min` to calculate line height dynamically based on the adjacent text column height:

```kotlin
// Establish dynamic timeline line tracking height matching details column
Row(modifier = Modifier.height(IntrinsicSize.Min)) {
    TimelineIndicator(color = bulletColor, isFirst = isFirst, isLast = isLast)
    ActivityDetailsColumn(log = log)
}
```

```
( Bullet Icon ) ────► Title: Create Workflow
       │
  [Vertical]
  [ Line ]
       │
( Bullet Icon ) ────► Title: Run Workflow
```

This ensures an unbroken vertical line matching exact content heights.

---

## 4. Monetization, Subscriptions, and Paywalls

Running AI models incurs server and API costs, necessitating a fair monetization framework. We implemented a dual model: weekly/yearly subscription options alongside consumable **Virtual Credits** deducted per node run based on computation complexity.

We integrated **RevenueCat's UI library** to present custom paywalls seamlessly:

```kotlin
// Render paywall screen dynamically
Paywall(options = PaywallOptions.Builder(dismissHandler = onDismiss).build())
```

---

## 5. Mapping Node Credit Rates

Not all AI nodes require the same compute resources. Generating an AI video via Runway Gen-3 is significantly more intensive than generating a static image via Google Gemini. 

We established a dynamic rate mapping where video generation consumes 5 credits while standard image generation costs 1 credit:

```kotlin
// Map custom credit consumption rates for AI models
val defaultRates = mapOf(
    NodeType.IMAGE_GENERATION to 1,  // Gemini
    NodeType.IMAGE_TO_VIDEO to 5,    // Runway Gen-3
    NodeType.TEXT_TO_SPEECH to 2
)
```

---

## 6. Instant Credit Sync & Polling Optimization

In-app virtual currency setups often suffer from SDK synchronization delays post-purchase. To ensure instant credit updates when users buy credit packages, we added an aggressive polling loop hitting RevenueCat backend endpoints every **500ms** (up to 10 iterations) right after purchase confirmation:

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

This eliminated previous latency delays (1.2s to 4s), displaying updated credits instantly across the canvas.

---

## Pocketflow is Live!

What started as an ambitious experiment is now live on the iOS App Store and Android Google Play Store! 

By leveraging **Compose Multiplatform** alongside **AI agentic workflows**, we designed, developed, audited, and launched a production cross-platform app in just 6 weeks. 

Thank you for following along on this building-in-public journey!
