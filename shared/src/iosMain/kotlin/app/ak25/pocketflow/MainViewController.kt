package app.ak25.pocketflow

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController(configure = {
    enforceStrictPlistSanityCheck = false
}) { App() }