package com.instrument

import androidx.compose.ui.window.ComposeUIViewController
import com.instrument.presentation.ui.AppNavGraph
import com.instrument.presentation.ui.theme.InstrumentTheme

fun MainViewController() = ComposeUIViewController {
    InstrumentTheme {
        AppNavGraph()
    }
}
