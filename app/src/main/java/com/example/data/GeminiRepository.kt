package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String? = null)

@JsonClass(generateAdapter = true)
data class GeminiContent(val role: String? = null, val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val service = retrofit.create(GeminiApiService::class.java)

    suspend fun getChatResponse(
        messages: List<Message>,
        systemInstructionText: String,
        temperature: Float = 0.7f
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: API Key is not set or is invalid in AI Studio Secrets."
        }

        // Map messages into GeminiContents
        val contents = messages.map { msg ->
            val roleStr = if (msg.role == "user") "user" else "model"
            GeminiContent(
                role = roleStr,
                parts = listOf(GeminiPart(text = msg.text))
            )
        }

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
            generationConfig = GeminiConfig(temperature = temperature)
        )

        try {
            val response = service.generateContent(apiKey, request)
            val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            return@withContext reply ?: "خطایی در دریافت پاسخ رخ داد. لطفاً دوباره تلاش کنید."
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error getting response from Gemini", e)
            return@withContext "Error: ${e.localizedMessage ?: "Unknown API request failure."}"
        }
    }
}
