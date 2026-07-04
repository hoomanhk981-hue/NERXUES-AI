package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val dbManager = LocalDatabaseManager(application)
    private val geminiRepository = GeminiRepository()

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<File>>(emptyList())
    val workspaceFiles: StateFlow<List<File>> = _workspaceFiles.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    private val _selectedFileContent = MutableStateFlow<String?>("")
    val selectedFileContent: StateFlow<String?> = _selectedFileContent.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    private val _organizerLogs = MutableStateFlow<List<String>>(emptyList())
    val organizerLogs: StateFlow<List<String>> = _organizerLogs.asStateFlow()

    private val _termuxScriptResult = MutableStateFlow<String?>(null)
    val termuxScriptResult: StateFlow<String?> = _termuxScriptResult.asStateFlow()

    // --- Voice Assistant & Text-to-Speech State ---
    private var tts: android.speech.tts.TextToSpeech? = null
    private var speechRecognizer: android.speech.SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _voiceInputText = MutableStateFlow("")
    val voiceInputText: StateFlow<String> = _voiceInputText.asStateFlow()

    private val _isAssistantTriggered = MutableStateFlow(false)
    val isAssistantTriggered: StateFlow<Boolean> = _isAssistantTriggered.asStateFlow()

    init {
        loadState()
        refreshWorkspaceFiles()
        setupTts()
    }

    // --- State Initialization & Storage Synchronization ---

    private fun loadState() {
        viewModelScope.launch {
            val state = dbManager.loadState()
            _uiState.value = state
            
            // Set first session as active if none set
            if (state.sessions.isNotEmpty()) {
                _currentSessionId.value = state.sessions.first().id
            } else {
                createNewSession("چت جدید")
            }
        }
    }

    private fun saveState() {
        viewModelScope.launch {
            dbManager.saveState(_uiState.value)
        }
    }

    // --- Chat Session Operations ---

    fun createNewSession(title: String = "چت جدید") {
        viewModelScope.launch {
            val newSession = ChatSession(
                id = UUID.randomUUID().toString(),
                title = title,
                messages = listOf(
                    Message(
                        role = "model",
                        text = "سلام! من دستیار هوشمند شما نکسوس هستم. آماده‌ام تا در کارهای گوشی، برنامه‌نویسی و مدیریت فایل‌ها به شما کمک کنم."
                    )
                )
            )
            val updatedSessions = _uiState.value.sessions + newSession
            _uiState.value = _uiState.value.copy(sessions = updatedSessions)
            _currentSessionId.value = newSession.id
            saveState()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val updatedSessions = _uiState.value.sessions.filter { it.id != sessionId }
            _uiState.value = _uiState.value.copy(sessions = updatedSessions)
            if (_currentSessionId.value == sessionId) {
                if (updatedSessions.isNotEmpty()) {
                    _currentSessionId.value = updatedSessions.first().id
                } else {
                    createNewSession()
                }
            }
            saveState()
        }
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    // --- Send Message & AI Execution ---

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _isWorking.value = true
            
            val currentId = _currentSessionId.value
            val stateValue = _uiState.value
            val sessionIndex = stateValue.sessions.indexOfFirst { it.id == currentId }
            if (sessionIndex == -1) return@launch

            val session = stateValue.sessions[sessionIndex]
            val updatedMessages = session.messages + Message(role = "user", text = text)
            
            // Update UI with user message immediately
            val updatedSessions = stateValue.sessions.toMutableList().apply {
                this[sessionIndex] = session.copy(
                    messages = updatedMessages,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            _uiState.value = stateValue.copy(sessions = updatedSessions)

            // Dynamic memory update (optional simulation)
            checkAndAddMemoriesAutomatically(text)

            // Determine if using local simulated model or cloud
            val responseText = if (_uiState.value.isLocalSimulation) {
                simulateOnDeviceModel(text)
            } else {
                // Generate context-aware system instructions with memories
                val memoryContext = _uiState.value.memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
                val systemPrompt = "${_uiState.value.systemPrompt}\n\nاطلاعات ذخیره شده و علایق کاربر (حافظه دستیار):\n$memoryContext"
                
                geminiRepository.getChatResponse(
                    messages = updatedMessages,
                    systemInstructionText = systemPrompt,
                    temperature = _uiState.value.modelTemperature
                )
            }

            // Append model response
            val finalMessages = updatedMessages + Message(role = "model", text = responseText)
            val finalSessions = _uiState.value.sessions.toMutableList().apply {
                val idx = indexOfFirst { it.id == currentId }
                if (idx != -1) {
                    this[idx] = this[idx].copy(
                        messages = finalMessages,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
            _uiState.value = _uiState.value.copy(sessions = finalSessions)
            saveState()
            _isWorking.value = false
            speak(responseText)
        }
    }

    private fun checkAndAddMemoriesAutomatically(text: String) {
        val lowerText = text.lowercase()
        // Simple heuristic extraction
        val memory = when {
            lowerText.contains("اسم من") || lowerText.contains("من علی") -> {
                val name = text.substringAfter("اسم من").substringBefore("است").trim().removePrefix(" ").removePrefix("ی")
                MemoryItem(UUID.randomUUID().toString(), "نام کاربر", name.ifEmpty { "علی" })
            }
            lowerText.contains("برنامه‌نویس") || lowerText.contains("برنامه نویس") -> {
                MemoryItem(UUID.randomUUID().toString(), "علایق شغلی", "توسعه‌دهنده نرم‌افزار / برنامه‌نویس")
            }
            lowerText.contains("علاقه دارم به") || lowerText.contains("پایتون دوست") -> {
                MemoryItem(UUID.randomUUID().toString(), "زبان برنامه‌نویسی ترجیحی", "پایتون (Python)")
            }
            else -> null
        }

        if (memory != null) {
            val updatedMemories = _uiState.value.memories.filter { it.key != memory.key } + memory
            _uiState.value = _uiState.value.copy(memories = updatedMemories)
        }
    }

    private fun simulateOnDeviceModel(prompt: String): String {
        return """[پردازش آفلاین روی مدل Gemma 2B]
درخواست شما دریافت شد: "$prompt"
به عنوان مدل هوش مصنوعی محلی (سفارشی)، در حال حاضر در حالت شبیه‌سازی آفلاین هستم تا به منابع رم گوشی شما فشار نیاید. 

💡 اطلاعات مفید:
- پایگاه داده محلی شما در فایل `chats_db.json` آپدیت شد.
- برای تجربه کامل هوش مصنوعی، لطفاً دکمه تغییر حالت (Simulated Local) را در تنظیمات غیرفعال کنید تا به هسته ابری متصل شوید."""
    }

    // --- VS Code-like Code Workspace Operations ---

    fun refreshWorkspaceFiles() {
        viewModelScope.launch {
            _workspaceFiles.value = dbManager.listWorkspaceFiles()
        }
    }

    fun selectWorkspaceFile(file: File) {
        viewModelScope.launch {
            _selectedFileName.value = file.name
            _selectedFileContent.value = dbManager.readWorkspaceFile(file.name) ?: ""
        }
    }

    fun closeActiveFile() {
        _selectedFileName.value = null
        _selectedFileContent.value = ""
    }

    fun saveActiveFile(content: String) {
        val fileName = _selectedFileName.value ?: return
        viewModelScope.launch {
            dbManager.writeWorkspaceFile(fileName, content)
            _selectedFileContent.value = content
            _operationStatus.value = "فایل $fileName با موفقیت ذخیره شد."
            refreshWorkspaceFiles()
        }
    }

    fun createNewFileInWorkspace(name: String, initialContent: String = "") {
        viewModelScope.launch {
            val file = dbManager.writeWorkspaceFile(name, initialContent)
            if (file != null) {
                _operationStatus.value = "فایل جدید ایجاد شد: $name"
                selectWorkspaceFile(file)
                refreshWorkspaceFiles()
            } else {
                _operationStatus.value = "خطا در ایجاد فایل جدید."
            }
        }
    }

    fun createNewFolderInWorkspace(name: String) {
        viewModelScope.launch {
            val success = dbManager.createWorkspaceFolder(name)
            if (success) {
                _operationStatus.value = "پوشه جدید ایجاد شد: $name"
                refreshWorkspaceFiles()
            } else {
                _operationStatus.value = "خطا در ایجاد پوشه جدید."
            }
        }
    }

    fun deleteWorkspaceFile(fileName: String) {
        viewModelScope.launch {
            val success = dbManager.deleteWorkspaceFile(fileName)
            if (success) {
                if (_selectedFileName.value == fileName) {
                    closeActiveFile()
                }
                _operationStatus.value = "فایل با موفقیت حذف شد."
                refreshWorkspaceFiles()
            } else {
                _operationStatus.value = "خطا در حذف فایل."
            }
        }
    }

    // --- Code Generation via AI ---

    fun generateCodeWithAI(prompt: String, fileExtension: String = "py") {
        if (prompt.isBlank()) return
        
        viewModelScope.launch {
            _isWorking.value = true
            _operationStatus.value = "در حال کدنویسی با هوش مصنوعی..."
            
            val systemInstruction = "You are a professional code writer. Write ONLY the raw source code based on the user request. DO NOT wrap the code in ```markdown markdown boxes. Do not include any explanations, introduction or conclusion. Write executable, solid code."
            
            val responseText = geminiRepository.getChatResponse(
                messages = listOf(Message(role = "user", text = prompt)),
                systemInstructionText = systemInstruction,
                temperature = 0.5f
            )
            
            // Clean code block ticks if LLM returned them anyway
            val cleanedCode = responseText
                .replace("```python", "")
                .replace("```javascript", "")
                .replace("```javascript", "")
                .replace("```html", "")
                .replace("```css", "")
                .replace("```bash", "")
                .replace("```sh", "")
                .replace("```", "")
                .trim()

            // Save to file
            val name = "ai_script_${System.currentTimeMillis() % 10000}.$fileExtension"
            val file = dbManager.writeWorkspaceFile(name, cleanedCode)
            if (file != null) {
                selectWorkspaceFile(file)
                refreshWorkspaceFiles()
                _operationStatus.value = "کد هوش مصنوعی در فایل $name ذخیره شد!"
            } else {
                _operationStatus.value = "خطا در ایجاد و ذخیره کد هوش مصنوعی."
            }
            
            _isWorking.value = false
        }
    }

    // --- Smart File Organizer Execution ---

    fun runSmartOrganizer() {
        viewModelScope.launch {
            _isWorking.value = true
            val logs = dbManager.runSmartFileOrganizer()
            _organizerLogs.value = logs
            refreshWorkspaceFiles()
            _isWorking.value = false
            _operationStatus.value = "فایل‌ها با موفقیت دسته‌بندی و مرتب شدند!"
        }
    }

    // --- Download Resource Manager ---

    fun downloadUrlResource(url: String, targetName: String) {
        if (url.isBlank() || targetName.isBlank()) return
        
        viewModelScope.launch {
            _isWorking.value = true
            _operationStatus.value = "در حال شروع دانلود..."
            
            val result = dbManager.downloadResource(url, targetName)
            result.onSuccess { msg ->
                _operationStatus.value = msg
                refreshWorkspaceFiles()
            }.onFailure { err ->
                _operationStatus.value = "خطا در دانلود فایل: ${err.message}"
            }
            
            _isWorking.value = false
        }
    }

    // --- Termux Script Execution Generator ---

    fun generateTermuxScriptForActiveFile() {
        val fileName = _selectedFileName.value
        val fileContent = _selectedFileContent.value
        if (fileName == null || fileContent == null) {
            _operationStatus.value = "ابتدا یک فایل را از پنل فایل‌ها انتخاب کنید."
            return
        }

        viewModelScope.launch {
            _isWorking.value = true
            val extension = File(fileName).extension.lowercase()
            
            val (pkgInstall, runCmd) = when (extension) {
                "py" -> Pair("pkg install python -y && pip install requests", "python $fileName")
                "js" -> Pair("pkg install nodejs -y", "node $fileName")
                "sh" -> Pair("chmod +x $fileName", "./$fileName")
                "html" -> Pair("pkg install python -y", "python -m http.server 8080 (برای اجرای وب‌سایت)")
                else -> Pair("", "cat $fileName")
            }

            val scriptText = """
# =======================================================
# دستورالعمل اجرای فایل در ترموکس (Termux)
# تولید شده توسط دستیار هوشمند نکسوس
# =======================================================

# ۱. ابتدا ترموکس را باز کرده و دسترسی به حافظه را بدهید:
termux-setup-storage

# ۲. به پوشه محیط کاری نکسوس بروید:
cd /data/data/${getApplication<Application>().packageName}/files/workspace

# ۳. پیش‌نیازها را نصب کنید (در صورت نیاز):
$pkgInstall

# ۴. برنامه را اجرا کنید:
$runCmd
""".trimIndent()
            
            _termuxScriptResult.value = scriptText
            _isWorking.value = false
            _operationStatus.value = "دستورات ترموکس با موفقیت تولید شد!"
        }
    }

    fun clearTermuxLogs() {
        _termuxScriptResult.value = null
    }

    // --- Memory / Preference Core Management ---

    fun addManualMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            val newItem = MemoryItem(UUID.randomUUID().toString(), key, value)
            val updatedMemories = _uiState.value.memories + newItem
            _uiState.value = _uiState.value.copy(memories = updatedMemories)
            saveState()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            val updatedMemories = _uiState.value.memories.filter { it.id != id }
            _uiState.value = _uiState.value.copy(memories = updatedMemories)
            saveState()
        }
    }

    fun setLocalSimulationMode(active: Boolean) {
        _uiState.value = _uiState.value.copy(isLocalSimulation = active)
        saveState()
    }

    fun updateModelTemperature(temp: Float) {
        _uiState.value = _uiState.value.copy(modelTemperature = temp)
        saveState()
    }

    fun updateSystemPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(systemPrompt = prompt)
        saveState()
    }

    fun clearOperationStatus() {
        _operationStatus.value = null
    }

    // --- Assistant Device Settings Intent Helper ---

    fun openAssistantSettings(context: Context) {
        try {
            // High-fidelity target settings intent for assistants
            val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("NexusViewModel", "Could not open assistant settings", e2)
            }
        }
    }

    // --- Google Play trigger helper ---
    fun openGooglePlayForTermux(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.termux")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.termux")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // --- Voice Assistant Implementation (STT & TTS) ---

    private fun setupTts() {
        tts = android.speech.tts.TextToSpeech(getApplication()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(java.util.Locale("fa"))
                if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(java.util.Locale.getDefault())
                }
            }
        }
    }

    fun speak(text: String) {
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), " [توضیح کد در صفحه نمایش داده شده است] ")
            .replace(Regex("[#*`_\\-\\[\\]()]"), " ")
            .trim()
        
        if (cleanText.isNotEmpty()) {
            tts?.speak(cleanText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "nexus_voice")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun triggerAssistantMode() {
        _isAssistantTriggered.value = true
        startListening()
    }

    fun dismissAssistantMode() {
        _isAssistantTriggered.value = false
        stopListening()
    }

    fun startListening() {
        val context = getApplication<Application>()
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceInputText.value = "در حال شنیدن صدای شما..."
            _isListening.value = true
            stopSpeaking()

            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                speechRecognizer?.destroy()
                speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : android.speech.RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            _isListening.value = false
                        }
                        override fun onError(error: Int) {
                            _isListening.value = false
                            val errMsg = when (error) {
                                android.speech.SpeechRecognizer.ERROR_AUDIO -> "خطای میکروفون"
                                android.speech.SpeechRecognizer.ERROR_CLIENT -> "خطای برنامه"
                                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازه دسترسی به میکروفون داده نشده"
                                android.speech.SpeechRecognizer.ERROR_NETWORK -> "عدم اتصال به اینترنت"
                                android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "صدا تشخیص داده نشد"
                                android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سرویس صوتی مشغول است"
                                else -> "خطای صوتی"
                            }
                            _voiceInputText.value = ""
                            _operationStatus.value = "تشخیص صدا ناموفق: $errMsg"
                        }
                        override fun onResults(results: android.os.Bundle?) {
                            val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val textResult = matches[0]
                                _voiceInputText.value = textResult
                                sendMessage(textResult)
                            }
                        }
                        override fun onPartialResults(partialResults: android.os.Bundle?) {
                            val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                _voiceInputText.value = matches[0]
                            }
                        }
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                    startListening(intent)
                }
            }
        } else {
            _operationStatus.value = "تشخیص صدا در این دستگاه در دسترس نیست"
        }
    }

    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
