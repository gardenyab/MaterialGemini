package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conversationId: Int,
    val sender: String, // "user" or "gemini"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageB64: String? = null,
    val imageMimeType: String? = null
)
