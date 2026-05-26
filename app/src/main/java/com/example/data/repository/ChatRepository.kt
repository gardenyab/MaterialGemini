package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.db.ChatDao
import com.example.data.db.Conversation
import com.example.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allConversations: Flow<List<Conversation>> = chatDao.getAllConversations()

    fun getMessagesForConversation(conversationId: Int): Flow<List<MessageEntity>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    suspend fun createConversation(title: String): Long = withContext(Dispatchers.IO) {
        chatDao.insertConversation(Conversation(title = title))
    }

    suspend fun updateConversationTitle(id: Int, title: String) = withContext(Dispatchers.IO) {
        chatDao.updateConversationTitle(id, title)
    }

    suspend fun deleteConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        chatDao.deleteConversation(conversation)
    }

    suspend fun clearHistory(conversationId: Int) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesForConversation(conversationId)
    }

    suspend fun insertMessage(message: MessageEntity): Long = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    /**
     * Sends a message to Gemini and stores both user input and API response in Room.
     */
    suspend fun sendMessage(
        conversationId: Int,
        promptText: String,
        history: List<MessageEntity>,
        isNewConversation: Boolean,
        selectedModel: String,
        customApiKey: String?,
        imageB64: String? = null,
        imageMimeType: String? = null
    ): String = withContext(Dispatchers.IO) {
        // 1. Save user message to database
        val userMessage = MessageEntity(
            conversationId = conversationId,
            sender = "user",
            text = promptText,
            imageB64 = imageB64,
            imageMimeType = imageMimeType
        )
        chatDao.insertMessage(userMessage)

        // If it was a new conversation with a default title, auto-rename based on prompt
        if (isNewConversation) {
            val shortTitle = if (promptText.length > 25) {
                promptText.substring(0, 25) + "..."
            } else {
                promptText
            }
            chatDao.updateConversationTitle(conversationId, shortTitle)
        }

        // 2. Format history + current message for Gemini
        val contents = mutableListOf<Content>()
        
        // Add existing conversation history
        for (msg in history) {
            val role = if (msg.sender == "user") "user" else "model"
            val partsList = mutableListOf<Part>()
            if (msg.imageB64 != null && msg.imageMimeType != null) {
                partsList.add(Part(inlineData = Blob(mimeType = msg.imageMimeType, data = msg.imageB64)))
            }
            partsList.add(Part(text = msg.text))
            contents.add(
                Content(
                    role = role,
                    parts = partsList
                )
            )
        }
        
        // Add current User message
        val currentParts = mutableListOf<Part>()
        if (imageB64 != null && imageMimeType != null) {
            currentParts.add(Part(inlineData = Blob(mimeType = imageMimeType, data = imageB64)))
        }
        currentParts.add(Part(text = promptText))
        contents.add(
            Content(
                role = "user",
                parts = currentParts
            )
        )

        // 3. Resolve API key with fallback
        val apiKey = if (!customApiKey.isNullOrEmpty()) customApiKey else getApiKey()

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            val errorMessage = "API-ключ не установлен! Пожалуйста, получите API-ключ на сайте Google AI Studio и введите его в настройках приложения (значок шестеренки в правом верхнем углу)."
            val errorEntity = MessageEntity(
                conversationId = conversationId,
                sender = "gemini",
                text = errorMessage
            )
            chatDao.insertMessage(errorEntity)
            return@withContext errorMessage
        }

        // 4. Call Gemini REST service
        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 2048
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = "You are a helpful and intelligent Gemini Chat Assistant. Answer accurately, clearly, and format your message properly with markdown formatting (bullet points, bold text, code blocks, tables, headers, etc.). Speak in Russian since the user communicates in Russian." ))
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(selectedModel, apiKey, request)
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "К сожалению, от Gemini не пришло текстового ответа."

            // 5. Store Gemini's response in Room
            val modelMessage = MessageEntity(
                conversationId = conversationId,
                sender = "gemini",
                text = replyText
            )
            chatDao.insertMessage(modelMessage)
            replyText
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error generation", e)
            val errorMessage = "Ошибка сети при обращении к Gemini API: ${e.localizedMessage ?: e.message}. Убедитесь, что интернет подключен."
            
            // Save the error reply message so the user sees it in the chat
            val errorEntity = MessageEntity(
                conversationId = conversationId,
                sender = "gemini",
                text = errorMessage
            )
            chatDao.insertMessage(errorEntity)
            errorMessage
        }
    }

    private fun getApiKey(): String {
        val configKey = BuildConfig.GEMINI_API_KEY
        return if (!configKey.isNullOrEmpty() && configKey != "MY_GEMINI_API_KEY" && configKey != "GEMINI_API_KEY") {
            configKey
        } else {
            ""
        }
    }
}
