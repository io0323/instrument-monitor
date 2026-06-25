package com.instrument.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.instrument.android.ui.AppNavGraph
import com.instrument.android.ui.theme.InstrumentTheme

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
