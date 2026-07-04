package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<Message> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class CodeFile(
    val name: String,
    val content: String,
    val language: String,
    val lastModified: Long = System.currentTimeMillis(),
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class MemoryItem(
    val id: String,
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class AppState(
    val sessions: List<ChatSession> = emptyList(),
    val codeFiles: List<CodeFile> = emptyList(),
    val memories: List<MemoryItem> = emptyList(),
    val isLocalSimulation: Boolean = false,
    val modelTemperature: Float = 0.7f,
    val systemPrompt: String = "You are Nexus AI, a highly capable on-device and cloud hybrid OS AI assistant. Speak elegantly in Persian and English. You can organize files, download resources, write code, and guide Termux users."
)
