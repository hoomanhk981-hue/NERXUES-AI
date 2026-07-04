package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.activity.viewModels
import com.example.ui.screens.NexusAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NexusViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: NexusViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Listen to device default assistant trigger (e.g. power button long press)
    if (intent?.action == Intent.ACTION_ASSIST) {
      viewModel.triggerAssistantMode()
    }

    setContent {
      MyApplicationTheme {
        NexusAppScreen(viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.action == Intent.ACTION_ASSIST) {
      viewModel.triggerAssistantMode()
    }
  }
}
