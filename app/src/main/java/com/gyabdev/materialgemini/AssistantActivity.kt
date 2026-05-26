package com.gyabdev.materialgemini

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyabdev.materialgemini.ui.screens.ChatHomeScreen
import com.gyabdev.materialgemini.ui.theme.MyApplicationTheme
import com.gyabdev.materialgemini.ui.viewmodel.ChatViewModel
import com.gyabdev.materialgemini.ui.viewmodel.ThemeMode

class AssistantActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[ChatViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Сразу включаем режим ассистента в ViewModel
        chatViewModel.setAssistantMode(true)

        setContent {
            val themeMode by chatViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by chatViewModel.dynamicColorEnabled.collectAsStateWithLifecycle()
            val isAssistantMode by chatViewModel.isAssistantMode.collectAsStateWithLifecycle()

            // Если режим ассистента выключен (нажали закрыть), закрываем Activity
            LaunchedEffect(isAssistantMode) {
                if (!isAssistantMode) {
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
                ChatHomeScreen(
                    viewModel = chatViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        chatViewModel.setAssistantMode(true)
    }
}
