package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.NexusViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusAppScreen(viewModel: NexusViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val workspaceFiles by viewModel.workspaceFiles.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFileContent by viewModel.selectedFileContent.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()
    val termuxScriptResult by viewModel.termuxScriptResult.collectAsState()
    val organizerLogs by viewModel.organizerLogs.collectAsState()

    // Collect voice and assistant triggers
    val isListening by viewModel.isListening.collectAsState()
    val voiceInputText by viewModel.voiceInputText.collectAsState()
    val isAssistantTriggered by viewModel.isAssistantTriggered.collectAsState()

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("chat") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(context, "جهت مکالمه صوتی با نکسوس، دسترسی به میکروفون الزامی است.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-trigger speech recording when default assistant is opened (e.g. power button)
    LaunchedEffect(isAssistantTriggered) {
        if (isAssistantTriggered) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasMicPermission) {
                viewModel.startListening()
            } else {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Display operation toasts automatically
    LaunchedEffect(operationStatus) {
        operationStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearOperationStatus()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == "chat",
                    onClick = { activeTab = "chat" },
                    icon = { Icon(Icons.Default.ChatBubble, contentDescription = "چت") },
                    label = { Text("چت نکسوس", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == "workspace",
                    onClick = { activeTab = "workspace" },
                    icon = { Icon(Icons.Default.Code, contentDescription = "کد نویسی") },
                    label = { Text("محیط کد", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == "assistant",
                    onClick = { activeTab = "assistant" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") },
                    label = { Text("دستیار و حافظه", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                "chat" -> ChatTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    currentSessionId = currentSessionId,
                    isWorking = isWorking,
                    onStartVoice = {
                        val hasMicPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasMicPermission) {
                            viewModel.startListening()
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                "workspace" -> WorkspaceTab(
                    viewModel = viewModel,
                    files = workspaceFiles,
                    selectedFileName = selectedFileName,
                    selectedFileContent = selectedFileContent,
                    isWorking = isWorking,
                    termuxScriptResult = termuxScriptResult,
                    organizerLogs = organizerLogs
                )
                "assistant" -> AssistantTab(viewModel = viewModel, uiState = uiState)
            }

            if (isListening) {
                VoiceAssistantOverlay(
                    viewModel = viewModel,
                    voiceInputText = voiceInputText,
                    onDismiss = { viewModel.stopListening() }
                )
            }
        }
    }
}

// ==========================================
// TAB 1: Chat interface (RTL-Friendly & Persian/English support)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTab(
    viewModel: NexusViewModel,
    uiState: com.example.data.AppState,
    currentSessionId: String,
    isWorking: Boolean,
    onStartVoice: () -> Unit
) {
    val activeSession = uiState.sessions.find { it.id == currentSessionId }
    var userMessageText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var showSessionMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat screen header with active session management
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "دستیار هوشمند نکسوس",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.isLocalSimulation) "حالت آفلاین محلی (Simulated Gemma)" else "متصل به Gemini-3.5-Flash Cloud",
                        fontSize = 11.sp,
                        color = if (uiState.isLocalSimulation) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
                }
            },
            actions = {
                IconButton(onClick = { showSessionMenu = !showSessionMenu }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "لیست چت‌ها")
                }
                IconButton(onClick = { viewModel.createNewSession("چت جدید") }) {
                    Icon(Icons.Default.Add, contentDescription = "چت جدید")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        // Session Selector Drawer / Dropdown overlay
        AnimatedVisibility(visible = showSessionMenu) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "چت‌های ذخیره شده در دیتابیس محلی (chats_db.json):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (uiState.sessions.isEmpty()) {
                        Text("هیچ چتی موجود نیست.")
                    } else {
                        uiState.sessions.forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSession(session.id)
                                        showSessionMenu = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = if (session.id == currentSessionId) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        session.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (session.id == currentSessionId) FontWeight.Bold else FontWeight.Normal,
                                        color = if (session.id == currentSessionId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (uiState.sessions.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteSession(session.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف چت", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Suggestions action bar to trigger smart tasks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { userMessageText = "فایل‌های محیط کار من رو به صورت مرتب سازماندهی و دسته‌بندی کن." },
                label = { Text("سازماندهی فایل‌ها", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { userMessageText = "یه برنامه ساده پایتون برای دانلود منابع از وب بنویس و ذخیرش کن." },
                label = { Text("کد پایتون دانلودر", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { userMessageText = "چطور می‌تونم ترموکس رو برای اجرای این اسکریپت‌ها کانفیگ کنم؟" },
                label = { Text("آموزش نصب ترموکس", fontSize = 12.sp) }
            )
        }

        // Main Chat Logs list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            reverseLayout = false
        ) {
            if (activeSession != null) {
                items(activeSession.messages) { message ->
                    ChatBubble(message = message)
                }
            }
        }

        // Spinner / Assistant processing line
        if (isWorking) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Message input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userMessageText,
                onValueChange = { userMessageText = it },
                placeholder = { Text("از من بپرسید... (مثلا: فایل را دانلود کن)") },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (userMessageText.isNotBlank()) {
                            viewModel.sendMessage(userMessageText)
                            userMessageText = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            // Dynamic microphone button for Speech-to-Text
            IconButton(
                onClick = onStartVoice,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Mic, contentDescription = "مکالمه صوتی", tint = Color.Black)
            }

            IconButton(
                onClick = {
                    if (userMessageText.isNotBlank()) {
                        viewModel.sendMessage(userMessageText)
                        userMessageText = ""
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Send, contentDescription = "ارسال", tint = Color.Black)
            }
        }
    }
}

@Composable
fun ChatBubble(message: com.example.data.Message) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) Color.Black else MaterialTheme.colorScheme.onSurface
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            if (!isUser) {
                Icon(
                    Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
            }
            Surface(
                color = bubbleColor,
                shape = shape,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = if (isUser) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// TAB 2: VS Code-like Code Workspace Screen
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceTab(
    viewModel: NexusViewModel,
    files: List<File>,
    selectedFileName: String?,
    selectedFileContent: String?,
    isWorking: Boolean,
    termuxScriptResult: String?,
    organizerLogs: List<String>
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Dialog state for file additions
    var showAddFileDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAIFileDialog by remember { mutableStateOf(false) }
    var showDownloaderDialog by remember { mutableStateOf(false) }
    
    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    
    // AI Code generator inputs
    var aiPrompt by remember { mutableStateOf("") }
    var aiExtension by remember { mutableStateOf("py") }

    // Downloader inputs
    var downloadUrl by remember { mutableStateOf("") }
    var downloadTargetName by remember { mutableStateOf("") }

    // Code Editor Content Local Buffer
    var editorContentBuffer by remember { mutableStateOf("") }

    // Keep editor sync with viewModel state updates
    LaunchedEffect(selectedFileContent) {
        editorContentBuffer = selectedFileContent ?: ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // VS Code Workspace Toolbar header
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ویرایشگر کد Nexus Code Space", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                // Play Store search link to download Termux app
                IconButton(onClick = { viewModel.openGooglePlayForTermux(context) }) {
                    Icon(Icons.Default.Shop, contentDescription = "دانلود ترموکس از پلی استور", tint = MaterialTheme.colorScheme.secondary)
                }
                // Run Smart File Organizer
                IconButton(onClick = { viewModel.runSmartOrganizer() }) {
                    Icon(Icons.Default.AutoMode, contentDescription = "سازماندهی هوشمند فایل‌ها", tint = MaterialTheme.colorScheme.tertiary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Workspace Left Drawer panel: Workspace Files list (Takes up 1/3 space)
            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.2f)
                    .padding(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("محیط کاری", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            IconButton(onClick = { showAddFileDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.NoteAdd, contentDescription = "فایل جدید", modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { showAddFolderDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "پوشه جدید", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { showAIFileDialog = true },
                            modifier = Modifier.weight(1f).height(28.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("کد نویسی هوشمند", fontSize = 10.sp, color = Color.Black)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { showDownloaderDialog = true },
                            modifier = Modifier.weight(0.9f).height(28.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("دانلود فایل", fontSize = 10.sp, color = Color.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()

                    // Files tree listing
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(files) { file ->
                            val isSelected = selectedFileName == file.name
                            val relativePath = file.parentFile?.name?.let { if (it == "workspace") "" else "$it/" } ?: ""
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectWorkspaceFile(file) }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$relativePath${file.name}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (!file.isDirectory) {
                                    IconButton(
                                        onClick = { viewModel.deleteWorkspaceFile(file.name) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف فایل", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Workspace Right Editor Canvas (Takes up 2/3 space)
            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(2f)
                    .padding(6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)), // VS Code Ultra Dark Color
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    if (selectedFileName == null) {
                        // Empty Welcome Screen
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.IntegrationInstructions, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Nexus Code Space v1.0",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                "برای شروع ویرایش یا دریافت راهنما، فایلی را از پنل سمت چپ انتخاب کرده یا از دکمه کدنویسی هوشمند استفاده کنید.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            // File organization logs indicator
                            if (organizerLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("گزارش مرتب‌سازی فایل‌ها:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                        organizerLogs.forEach { log ->
                                            Text("• $log", fontSize = 10.sp, color = Color.LightGray)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Editor Panel Toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(selectedFileName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                            Row {
                                IconButton(
                                    onClick = { viewModel.saveActiveFile(editorContentBuffer) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "ذخیره فایل", tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.generateTermuxScriptForActiveFile() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "اجرا در ترموکس", tint = MaterialTheme.colorScheme.tertiary)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.closeActiveFile() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "بستن فایل", tint = Color.LightGray)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Live editor code workspace
                        OutlinedTextField(
                            value = editorContentBuffer,
                            onValueChange = { editorContentBuffer = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFC9D1D9) // Github dark code color
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color(0xFF0D1117),
                                unfocusedContainerColor = Color(0xFF0D1117)
                            )
                        )

                        // Termux execution guide card overlay
                        AnimatedVisibility(visible = termuxScriptResult != null) {
                            termuxScriptResult?.let { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(top = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("راه‌اندازی و اجرای فایل در Termux:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(result))
                                                        Toast.makeText(context, "کپی شد!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی دستورات", modifier = Modifier.size(14.dp), tint = Color.LightGray)
                                                }
                                                IconButton(onClick = { viewModel.clearTermuxLogs() }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Close, contentDescription = "بستن", modifier = Modifier.size(14.dp), tint = Color.Red)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                            Text(
                                                text = result,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Green,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS CONTROLLERS ---

    // 1. Add file dialog
    if (showAddFileDialog) {
        AlertDialog(
            onDismissRequest = { showAddFileDialog = false },
            title = { Text("ایجاد فایل جدید", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text("مثلا: script.py") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isNotBlank()) {
                        viewModel.createNewFileInWorkspace(newFileName)
                        newFileName = ""
                        showAddFileDialog = false
                    }
                }) { Text("ایجاد فایل") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFileDialog = false }) { Text("انصراف") }
            }
        )
    }

    // 2. Add folder dialog
    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("ایجاد پوشه جدید", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("مثلا: custom_folder") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.createNewFolderInWorkspace(newFolderName)
                        newFolderName = ""
                        showAddFolderDialog = false
                    }
                }) { Text("ایجاد پوشه") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text("انصراف") }
            }
        )
    }

    // 3. AI code writer input dialog
    if (showAIFileDialog) {
        AlertDialog(
            onDismissRequest = { showAIFileDialog = false },
            title = { Text("کدنویسی با هوش مصنوعی نکسوس", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("درخواست خود را وارد کنید، هوش مصنوعی فایل کد را تولید کرده و ذخیره می‌کند:", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("یک اسکریپت ساده پایتون برای دانلود عکس از سایت فلان...") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("فرمت فایل:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick = { aiExtension = "py" }, label = { Text("Python (.py)") }, border = BorderStroke(1.dp, if (aiExtension == "py") MaterialTheme.colorScheme.primary else Color.Gray))
                        SuggestionChip(onClick = { aiExtension = "js" }, label = { Text("NodeJS (.js)") }, border = BorderStroke(1.dp, if (aiExtension == "js") MaterialTheme.colorScheme.primary else Color.Gray))
                        SuggestionChip(onClick = { aiExtension = "html" }, label = { Text("HTML (.html)") }, border = BorderStroke(1.dp, if (aiExtension == "html") MaterialTheme.colorScheme.primary else Color.Gray))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (aiPrompt.isNotBlank()) {
                        viewModel.generateCodeWithAI(aiPrompt, aiExtension)
                        aiPrompt = ""
                        showAIFileDialog = false
                    }
                }) { Text("تولید و ذخیره کد") }
            },
            dismissButton = {
                TextButton(onClick = { showAIFileDialog = false }) { Text("انصراف") }
            }
        )
    }

    // 4. Download file manager dialog
    if (showDownloaderDialog) {
        AlertDialog(
            onDismissRequest = { showDownloaderDialog = false },
            title = { Text("دانلود مستقیم فایل از اینترنت", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it },
                        placeholder = { Text("آدرس URL فایل (HTTPS)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = downloadTargetName,
                        onValueChange = { downloadTargetName = it },
                        placeholder = { Text("نام فایل مقصد در گوشی (مثلا: index.html)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (downloadUrl.isNotBlank() && downloadTargetName.isNotBlank()) {
                        viewModel.downloadUrlResource(downloadUrl, downloadTargetName)
                        downloadUrl = ""
                        downloadTargetName = ""
                        showDownloaderDialog = false
                    }
                }) { Text("شروع دانلود") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloaderDialog = false }) { Text("انصراف") }
            }
        )
    }
}

// ==========================================
// TAB 3: Assistant customization & Memories Panel (Local SQLite database mock)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantTab(
    viewModel: NexusViewModel,
    uiState: com.example.data.AppState
) {
    val context = LocalContext.current
    var memoryKey by remember { mutableStateOf("") }
    var memoryValue by remember { mutableStateOf("") }
    
    var systemPromptInput by remember { mutableStateOf(uiState.systemPrompt) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Assistant Quick Setup
        Text(
            text = "تنظیمات دستیار اختصاصی گوشی",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ثبت نکسوس به عنوان دستیار پیش‌فرض اندروید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "با لمس دکمه زیر به تنظیمات گوشی منتقل خواهید شد. در بخش Default Apps، گزینه Assist App را انتخاب کرده و Nexus AI را برگزینید تا با نگه‌داشتن دکمه هوم نکسوس باز شود.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.openAssistantSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Android, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("باز کردن تنظیمات دستیار سیستم", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Engine Configuration Toggle
        Text(
            text = "هسته هوش هوش مصنوعی (AI Engine)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("شبیه‌ساز هوش آفلاین محلی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("غیرفعال بودن این دکمه به معنی استفاده از وب‌سرویس مستقیم Gemini Cloud است.", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.isLocalSimulation,
                        onCheckedChange = { viewModel.setLocalSimulationMode(it) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Text("نرخ خلاقیت (Temperature): ${uiState.modelTemperature}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = uiState.modelTemperature,
                    onValueChange = { viewModel.updateModelTemperature(it) },
                    valueRange = 0.1f..1.0f,
                    steps = 9
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("دستورالعمل سیستمی دستیار:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = systemPromptInput,
                    onValueChange = {
                        systemPromptInput = it
                        viewModel.updateSystemPrompt(it)
                    },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Personal Memories Database List (JSON based memory)
        Text(
            text = "بانک اطلاعات شخصی دستیار (حافظه اختصاصی)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "به نکسوس ویژگی‌های خود را آموزش دهید تا آن‌ها را به یاد بیاورد و شخصی‌سازی کند:",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = memoryKey,
                        onValueChange = { memoryKey = it },
                        placeholder = { Text("ویژگی (مثلا: نام من)") },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = memoryValue,
                        onValueChange = { memoryValue = it },
                        placeholder = { Text("مقدار (مثلا: علی)") },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        if (memoryKey.isNotBlank() && memoryValue.isNotBlank()) {
                            viewModel.addManualMemory(memoryKey, memoryValue)
                            memoryKey = ""
                            memoryValue = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ذخیره در حافظه دیتابیس", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("لیست اطلاعات ذخیره شده در chats_db.json:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                
                if (uiState.memories.isEmpty()) {
                    Text("هیچ حافظه‌ای ذخیره نشده است.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                } else {
                    uiState.memories.forEach { memory ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(memory.key, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(memory.value, fontSize = 11.sp, color = Color.LightGray)
                            }
                            IconButton(
                                onClick = { viewModel.deleteMemory(memory.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف حافظه", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ==========================================
// GEMINI-LIKE GLOWING VOICE OVERLAY
// ==========================================

@Composable
fun VoiceAssistantOverlay(
    viewModel: NexusViewModel,
    voiceInputText: String,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    // Rotating angle for the Gemini neon glow border
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // Bouncing bar heights for the voice visualizer
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val scale4 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = false) {}, // prevent click-through
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(
                width = 2.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF9061F9), // Purple
                        Color(0xFF3F83F8), // Blue
                        Color(0xFF0E9F6E), // Green
                        Color(0xFF9061F9)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "دستیار صوتی نکسوس",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { viewModel.stopSpeaking() }) {
                        Icon(
                            imageVector = Icons.Default.VolumeMute,
                            contentDescription = "قطع صدا",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pulsing voice animation waves
                Row(
                    modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val barWidth = 6.dp
                    val spacing = 4.dp
                    
                    Box(modifier = Modifier.size(barWidth, 60.dp * scale1).clip(RoundedCornerShape(50)).background(Color(0xFF9061F9)))
                    Spacer(modifier = Modifier.width(spacing))
                    Box(modifier = Modifier.size(barWidth, 60.dp * scale2).clip(RoundedCornerShape(50)).background(Color(0xFF3F83F8)))
                    Spacer(modifier = Modifier.width(spacing))
                    Box(modifier = Modifier.size(barWidth, 60.dp * scale3).clip(RoundedCornerShape(50)).background(Color(0xFF0E9F6E)))
                    Spacer(modifier = Modifier.width(spacing))
                    Box(modifier = Modifier.size(barWidth, 60.dp * scale4).clip(RoundedCornerShape(50)).background(Color(0xFF9061F9)))
                    Spacer(modifier = Modifier.width(spacing))
                    Box(modifier = Modifier.size(barWidth, 60.dp * scale2).clip(RoundedCornerShape(50)).background(Color(0xFF3F83F8)))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time voice result / listening text
                Text(
                    text = if (voiceInputText.isEmpty()) "درحال شنیدن صدای شما..." else voiceInputText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Guide hint
                Text(
                    text = "می‌توانید کارهایی مثل «فایل‌های من را سازماندهی کن» یا «یک اسکریپت پایتون بنویس» را صوتی بگویید.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

