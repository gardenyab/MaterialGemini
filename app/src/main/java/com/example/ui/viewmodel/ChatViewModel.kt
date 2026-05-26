package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.Conversation
import com.example.data.db.MessageEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.chatDao())

    private val sharedPrefs = application.getSharedPreferences("gemini_chat_prefs", Context.MODE_PRIVATE)

    // Reactive lists of all saved chat conversations
    val conversations: StateFlow<List<Conversation>> = repository.allConversations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current active chat session
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    // Dynamically query messages whenever the active chat session changes
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _currentConversation
        .flatMapLatest { conv ->
            if (conv != null) {
                repository.getMessagesForConversation(conv.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Loading/Writing status for Gemini network activity
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Persistent Settings
    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(sharedPrefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("dynamic_colors", true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    init {
        // Automatically open the most recent conversation if one exists
        viewModelScope.launch {
            conversations.firstOrNull { it.isNotEmpty() }?.firstOrNull()?.let { lastConv ->
                _currentConversation.value = lastConv
            }
        }
    }

    fun selectConversation(conversation: Conversation?) {
        _currentConversation.value = conversation
    }

    fun startNewConversation() {
        viewModelScope.launch {
            // Create in Room with placeholder title, will auto update on first user message
            val id = repository.createConversation("Новый чат")
            val newConv = Conversation(id = id.toInt(), title = "Новый чат")
            _currentConversation.value = newConv
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            // If deleting the current open conversation, clear selected state
            if (_currentConversation.value?.id == conversation.id) {
                _currentConversation.value = null
            }
            repository.deleteConversation(conversation)
            
            // Try to open first item from remain chats after deletion
            val remaining = conversations.value.filter { it.id != conversation.id }
            if (remaining.isNotEmpty()) {
                _currentConversation.value = remaining.first()
            }
        }
    }

    fun sendMessage(promptText: String) {
        if (promptText.isBlank()) return
        val conv = _currentConversation.value ?: return

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                // Determine if this is the first message (current title starting as 'Новый чат')
                val isNew = conv.title == "Новый чат" && messages.value.isEmpty()
                
                // Get snapshots of existing conversation history
                val currentHistory = messages.value
                
                // Fire API and update DB logic inside repository
                repository.sendMessage(
                    conversationId = conv.id,
                    promptText = promptText,
                    history = currentHistory,
                    isNewConversation = isNew
                )
                
                // If it was a new conversation, refresh the active object reference with the updated title
                if (isNew) {
                    val updatedTitle = if (promptText.length > 25) promptText.substring(0, 25) + "..." else promptText
                    _currentConversation.value = conv.copy(title = updatedTitle)
                }

            } catch (e: Exception) {
                // Handled in repository itself, showing text in screen
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun changeThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
        sharedPrefs.edit().putBoolean("dynamic_colors", enabled).apply()
    }
}
