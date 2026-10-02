package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assistant.MahiViewModel
import com.example.assistant.ui.MahiScreen
import com.example.ui.theme.MahiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MahiTheme {
                val viewModel: MahiViewModel = viewModel()
                MahiScreen(viewModel = viewModel)
            }
        }
    }
}
