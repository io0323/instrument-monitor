package com.instrument.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.instrument.presentation.ui.AppNavGraph
import com.instrument.presentation.ui.theme.InstrumentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InstrumentTheme {
                AppNavGraph()
            }
        }
    }
}
