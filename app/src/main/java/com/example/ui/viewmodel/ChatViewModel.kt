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

    private val _apiKey = MutableStateFlow(sharedPrefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(sharedPrefs.getString("selected_model", "gemini-3.5-flash") ?: "gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Digital Assistant Mode States
    private val _isAssistantMode = MutableStateFlow(false)
    val isAssistantMode: StateFlow<Boolean> = _isAssistantMode.asStateFlow()

    private val _assistantResponse = MutableStateFlow<String?>(null)
    val assistantResponse: StateFlow<String?> = _assistantResponse.asStateFlow()

    private val _isAssistantGenerating = MutableStateFlow(false)
    val isAssistantGenerating: StateFlow<Boolean> = _isAssistantGenerating.asStateFlow()

    private val _assistantConversation = MutableStateFlow<Conversation?>(null)
    val assistantConversation: StateFlow<Conversation?> = _assistantConversation.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val assistantMessages: StateFlow<List<MessageEntity>> = _assistantConversation
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

    fun sendMessage(promptText: String, imageB64: String? = null, imageMimeType: String? = null) {
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
                    isNewConversation = isNew,
                    selectedModel = _selectedModel.value,
                    customApiKey = _apiKey.value.takeIf { it.isNotBlank() },
                    imageB64 = imageB64,
                    imageMimeType = imageMimeType
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

    fun sendAssistantMessage(promptText: String, imageB64: String? = null, imageMimeType: String? = null) {
        if (promptText.isBlank()) return
        viewModelScope.launch {
            _isAssistantGenerating.value = true
            _assistantResponse.value = null
            try {
                var conv = _assistantConversation.value
                val isNew = conv == null
                if (isNew) {
                    val titleSnippet = if (promptText.length > 20) promptText.take(20) + "..." else promptText
                    val id = repository.createConversation("Ассистент: $titleSnippet")
                    conv = Conversation(id = id.toInt(), title = "Ассистент: $titleSnippet")
                    _assistantConversation.value = conv
                }

                // 1. Save user message in the Room database
                val userMessage = MessageEntity(
                    conversationId = conv!!.id,
                    sender = "user",
                    text = promptText,
                    imageB64 = imageB64,
                    imageMimeType = imageMimeType
                )
                repository.insertMessage(userMessage)

                // 2. Fetch all current messages for this session
                val history = assistantMessages.value

                // 3. Reconstruct contents for Google's Gemini API request
                val contents = mutableListOf<com.example.data.api.Content>()
                for (msg in history) {
                    val role = if (msg.sender == "user") "user" else "model"
                    val partsList = mutableListOf<com.example.data.api.Part>()
                    if (msg.imageB64 != null && msg.imageMimeType != null) {
                        partsList.add(com.example.data.api.Part(inlineData = com.example.data.api.Blob(mimeType = msg.imageMimeType, data = msg.imageB64)))
                    }
                    partsList.add(com.example.data.api.Part(text = msg.text))
                    contents.add(com.example.data.api.Content(role = role, parts = partsList))
                }

                // Plus the newest User message
                val currentParts = mutableListOf<com.example.data.api.Part>()
                if (imageB64 != null && imageMimeType != null) {
                    currentParts.add(com.example.data.api.Part(inlineData = com.example.data.api.Blob(mimeType = imageMimeType, data = imageB64)))
                }
                currentParts.add(com.example.data.api.Part(text = promptText))
                contents.add(com.example.data.api.Content(role = "user", parts = currentParts))

                val configKey = com.example.BuildConfig.GEMINI_API_KEY
                val fallbackKey = if (!configKey.isNullOrEmpty() && configKey != "MY_GEMINI_API_KEY" && configKey != "GEMINI_API_KEY") configKey else ""
                val apiKeyToUse = if (_apiKey.value.isNotBlank()) _apiKey.value else fallbackKey

                if (apiKeyToUse.isBlank()) {
                    val errorText = "Ошибка: API-ключ не установлен! Пожалуйста, укажите ваш персональный API-ключ Gemini в настройках приложения (значок шестеренки в правом верхнем углу)."
                    val errorEntity = MessageEntity(
                        conversationId = conv.id,
                        sender = "gemini",
                        text = errorText
                    )
                    repository.insertMessage(errorEntity)
                    _isAssistantGenerating.value = false
                    return@launch
                }

                val request = com.example.data.api.GenerateContentRequest(
                    contents = contents,
                    systemInstruction = com.example.data.api.Content(
                        parts = listOf(com.example.data.api.Part(text = "You are a helpful and intelligent Gemini Digital Assistant. Provide crisp, concise, informative answers (about 1-4 sentences) fitted for quick reading Russian language user since they communicate in Russian."))
                    )
                )

                val response = com.example.data.api.RetrofitClient.service.generateContent(
                    _selectedModel.value,
                    apiKeyToUse,
                    request
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "К сожалению, ассистенту не удалось получить ответ от Gemini."

                val modelMessage = MessageEntity(
                    conversationId = conv.id,
                    sender = "gemini",
                    text = replyText
                )
                repository.insertMessage(modelMessage)
                _assistantResponse.value = replyText

            } catch (e: Exception) {
                val errorMsg = "Ошибка ассистента: ${e.localizedMessage ?: e.message}"
                val conv = _assistantConversation.value
                if (conv != null) {
                    repository.insertMessage(
                        MessageEntity(
                            conversationId = conv.id,
                            sender = "gemini",
                            text = errorMsg
                        )
                    )
                }
                _assistantResponse.value = errorMsg
            } finally {
                _isAssistantGenerating.value = false
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

    fun setApiKey(key: String) {
        _apiKey.value = key
        sharedPrefs.edit().putString("api_key", key).apply()
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        sharedPrefs.edit().putString("selected_model", model).apply()
    }

    fun setAssistantMode(enabled: Boolean) {
        _isAssistantMode.value = enabled
        if (enabled) {
            _assistantConversation.value = null
        }
        _assistantResponse.value = null
    }
}
