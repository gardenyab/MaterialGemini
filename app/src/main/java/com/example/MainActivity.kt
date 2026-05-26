package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ChatHomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ThemeMode

class MainActivity : ComponentActivity() {
  private val chatViewModel: ChatViewModel by lazy {
    androidx.lifecycle.ViewModelProvider(this)[ChatViewModel::class.java]
  }
  private var lastLaunchWasAssist = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (intent?.action == Intent.ACTION_ASSIST) {
      lastLaunchWasAssist = true
      chatViewModel.setAssistantMode(true)
    }

    setContent {
      val themeMode by chatViewModel.themeMode.collectAsStateWithLifecycle()
      val dynamicColor by chatViewModel.dynamicColorEnabled.collectAsStateWithLifecycle()
      val isAssistantMode by chatViewModel.isAssistantMode.collectAsStateWithLifecycle()

      LaunchedEffect(isAssistantMode) {
        if (!isAssistantMode && lastLaunchWasAssist) {
          finish()
        }
      }

      val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

      MyApplicationTheme(
        darkTheme = isDarkTheme,
        dynamicColor = dynamicColor
      ) {
        if (lastLaunchWasAssist) {
          if (isAssistantMode) {
            ChatHomeScreen(
              viewModel = chatViewModel,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
            )
          }
        } else {
          ChatHomeScreen(
            viewModel = chatViewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.action == Intent.ACTION_ASSIST) {
      lastLaunchWasAssist = true
      chatViewModel.setAssistantMode(true)
    } else {
      lastLaunchWasAssist = false
    }
  }
}
