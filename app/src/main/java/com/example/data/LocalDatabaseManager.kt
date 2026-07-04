package com.example.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

class LocalDatabaseManager(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val appStateAdapter = moshi.adapter(AppState::class.java)

    private val dbFile = File(context.filesDir, "chats_db.json")
    val workspaceDir = File(context.filesDir, "workspace")

    init {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
            // Create some default code files for a rich starter experience
            createStarterFiles()
        }
    }

    private fun createStarterFiles() {
        val welcomePy = File(workspaceDir, "welcome.py")
        if (!welcomePy.exists()) {
            welcomePy.writeText("""# Welcome to Nexus AI Workspace
# This script executes standard greetings and counts prime numbers.

def is_prime(n):
    if n <= 1:
        return False
    for i in range(2, int(n**0.5) + 1):
        if n % i == 0:
            return False
    return True

print("Hello from Nexus AI Assistant!")
primes = [x for x in range(1, 50) if is_prime(x)]
print(f"Prime numbers under 50: {primes}")
""")
        }

        val testJs = File(workspaceDir, "test.js")
        if (!testJs.exists()) {
            testJs.writeText("""// Node.js sample script written by Nexus AI
console.log("Analyzing file organization patterns...");
const list = ["Python", "JavaScript", "HTML", "JSON"];
list.forEach((item, index) => {
    console.log(`${'$'}{index + 1}: ${'$'}{item}`);
});
""")
        }
        
        val todoTxt = File(workspaceDir, "todo.txt")
        if (!todoTxt.exists()) {
            todoTxt.writeText("""- Learn Termux setup
- Try AI file organizer
- Test assistant deep-link
- Download sample HTML page
""")
        }
    }

    // --- JSON AppState Database ---

    suspend fun loadState(): AppState = withContext(Dispatchers.IO) {
        try {
            if (dbFile.exists()) {
                val jsonStr = dbFile.readText()
                val state = appStateAdapter.fromJson(jsonStr)
                if (state != null) {
                    return@withContext state
                }
            }
        } catch (e: Exception) {
            Log.e("LocalDatabaseManager", "Error loading app state", e)
        }
        
        // Return default state with a starter chat session
        val starterSession = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "چت جدید با دستیار نکسوس",
            messages = listOf(
                Message(
                    role = "model",
                    text = "سلام! من نکسوس هستم، دستیار هوشمند شما. من می‌توانم فایل‌های گوشی شما را مدیریت کنم، دانلودهای اینترنتی انجام دهم، ویرایشگر کدی شبیه VS Code در اختیارتان بگذارم و حتی با ترموکس ارتباط برقرار کنم. چطور می‌توانم کمکتان کنم؟"
                )
            )
        )
        val defaultState = AppState(
            sessions = listOf(starterSession),
            memories = listOf(
                MemoryItem(UUID.randomUUID().toString(), "نام دستیار", "نکسوس (Nexus AI)"),
                MemoryItem(UUID.randomUUID().toString(), "محیط کاری", "کتابخانه Workspace داخلی")
            )
        )
        saveState(defaultState)
        return@withContext defaultState
    }

    suspend fun saveState(state: AppState) = withContext(Dispatchers.IO) {
        try {
            val jsonStr = appStateAdapter.toJson(state)
            dbFile.writeText(jsonStr)
        } catch (e: Exception) {
            Log.e("LocalDatabaseManager", "Error saving app state", e)
        }
    }

    // --- Real File Operations (Workspace) ---

    suspend fun listWorkspaceFiles(): List<File> = withContext(Dispatchers.IO) {
        return@withContext listFilesRecursively(workspaceDir)
    }

    private fun listFilesRecursively(dir: File): List<File> {
        val result = mutableListOf<File>()
        val files = dir.listFiles() ?: return emptyList()
        for (f in files) {
            if (f.name.startsWith(".")) continue
            result.add(f)
            if (f.isDirectory) {
                result.addAll(listFilesRecursively(f))
            }
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun writeWorkspaceFile(fileName: String, content: String): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(workspaceDir, fileName)
            // Ensure parent dirs exist if user saved inside a subfolder
            file.parentFile?.mkdirs()
            file.writeText(content)
            return@withContext file
        } catch (e: Exception) {
            Log.e("LocalDatabaseManager", "Error writing file", e)
            return@withContext null
        }
    }

    suspend fun readWorkspaceFile(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(workspaceDir, fileName)
            if (file.exists() && file.isFile) {
                return@withContext file.readText()
            }
        } catch (e: Exception) {
            Log.e("LocalDatabaseManager", "Error reading file", e)
        }
        return@withContext null
    }

    suspend fun deleteWorkspaceFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(workspaceDir, fileName)
            if (file.exists()) {
                return@withContext file.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("LocalDatabaseManager", "Error deleting file", e)
        }
        return@withContext false
    }

    suspend fun createWorkspaceFolder(folderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = File(workspaceDir, folderName)
            return@withContext folder.mkdirs()
        } catch (e: Exception) {
            Log.e("LocalDatabaseManager", "Error creating directory", e)
            return@withContext false
        }
    }

    // --- AI Smart File Organizer ---
    // Reads files inside workspace, moves files to dedicated category folders
    suspend fun runSmartFileOrganizer(): List<String> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val files = workspaceDir.listFiles() ?: return@withContext emptyList()
        
        for (file in files) {
            if (file.isDirectory) continue
            val ext = file.extension.lowercase()
            val targetFolder = when (ext) {
                "py", "js", "cpp", "java", "sh" -> "scripts"
                "html", "css" -> "web"
                "txt", "md", "json" -> "documents"
                "jpg", "jpeg", "png", "gif" -> "media"
                else -> "others"
            }
            
            val destDir = File(workspaceDir, targetFolder)
            if (!destDir.exists()) {
                destDir.mkdirs()
                logs.add("پوشه جدید ایجاد شد: $targetFolder/")
            }
            
            val destFile = File(destDir, file.name)
            if (file.renameTo(destFile)) {
                logs.add("فایل انتقال یافت: ${file.name} ➡️ $targetFolder/${file.name}")
            } else {
                logs.add("خطا در انتقال فایل: ${file.name}")
            }
        }
        
        if (logs.isEmpty()) {
            logs.add("همه فایل‌ها در حال حاضر سازماندهی شده هستند.")
        }
        return@withContext logs
    }

    // --- File Downloader ---
    // Downloads content from the internet and saves it into workspace
    suspend fun downloadResource(url: String, targetFileName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("دانلود ناموفق بود. کد پاسخ: ${response.code}"))
                }
                
                val body = response.body ?: return@withContext Result.failure(Exception("بدنه پاسخ خالی بود"))
                val file = File(workspaceDir, targetFileName)
                file.parentFile?.mkdirs()
                
                // Save file content
                file.writeBytes(body.bytes())
                return@withContext Result.success("فایل با موفقیت دانلود و ذخیره شد: $targetFileName (${file.length()} بایت)")
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
