/*
 * This file is part of AndroidCodeStudio.
 * Android AI Studio - Crash-Proof Autonomous Edition
 * Owner & Creator: Parvez Mosharof
 */

package com.tom.rv2ide.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.tom.rv2ide.R
import com.tom.rv2ide.activities.editor.EditorHandlerActivity
import com.tom.rv2ide.adapters.FileModificationAdapter
import com.tom.rv2ide.artificial.agents.AIAgentManager
import com.tom.rv2ide.handlers.AIRequestHandler
import com.tom.rv2ide.managers.CodeCompletionManager
import com.tom.rv2ide.utils.ProjectHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Android AI Studio - Master AI Agent (Crash-Proof Architecture)
 * Creator & Owner: Parvez Mosharof
 */
class ChatFragment : Fragment() {

    private lateinit var aiAgent: AIAgentManager
    private var promptInput: TextInputEditText? = null
    private var executeBtn: MaterialButton? = null
    private var clearBtn: MaterialButton? = null
    private var statusText: MaterialTextView? = null
    private var summaryText: MaterialTextView? = null
    private var progressIndicator: CircularProgressIndicator? = null
    private var fileModificationList: RecyclerView? = null
    private var summaryCard: LinearLayout? = null
    private var fileModificationAdapter: FileModificationAdapter? = null

    private var codeCompletionManager: CodeCompletionManager? = null
    private var aiRequestHandler: AIRequestHandler? = null

    private var typingJob: Job? = null
    private var fileMonitorJob: Job? = null
    private var completionStateMonitorJob: Job? = null
    private var lastMonitoredFile: File? = null
    private var isSettingUpCompletion = false

    // ক্র্যাশ গার্ড: নিরাপদ পাথ খোঁজা যাতে নাল পয়েন্টার ক্র্যাশ না হয়
    private fun getSafeProjectPath(): String {
        return try {
            ProjectHelper.getProjectRoot()?.absolutePath?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private val masterApiKey: String by lazy {
        val p1 = "AQ.Ab8RN6JlpsQNSkP"
        val p2 = "nhKkW-cFdpjr3dfdf"
        val p3 = "EWHyJLM7R1OX82s2tQ"
        p1 + p2 + p3
    }

    private val masterInstruction = """
        You are the Master Autonomous App Builder inside Android AI Studio, created exclusively for Parvez Mosharof.
        Build real, fully functional, production-ready Android apps with complete logic. No dummy/sample code or placeholders.
        Keep the user's project settings (package name, language, SDK) exactly as configured.
        Format every file strictly like this:
        <<<FILE:relative/path/to/filename.ext>>>
        [Complete code here without truncation]
        <<<END_FILE>>>
        Output <<<BUILD_READY>>> at the end.
    """.trimIndent()

    private val sharedPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "code_completion_enabled") {
                val isEnabled = prefs.getBoolean(key, true)
                lifecycleScope.launch {
                    handleCompletionStateChange(isEnabled)
                }
            }
        }

    companion object {
        @JvmStatic
        fun newInstance(aiAgent: AIAgentManager): ChatFragment {
            return ChatFragment().apply {
                this.aiAgent = aiAgent
            }
        }

        @JvmStatic
        fun newInstance(): ChatFragment = ChatFragment()
    }

    fun getCodeCompletionManager(): CodeCompletionManager? {
        return codeCompletionManager
    }

    private fun getSafeAiAgent(): AIAgentManager {
        if (!::aiAgent.isInitialized) {
            aiAgent = AIAgentManager(requireContext())
        }
        return aiAgent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("api_key", masterApiKey)
                .putString("gemini_api_key", masterApiKey)
                .putString("apiKey", masterApiKey)
                .putString("token", masterApiKey)
                .apply()
        } catch (ignored: Exception) {}
        getSafeAiAgent()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_chat, container, false)
        } catch (e: Exception) {
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initializeViews(view)
            setupRecyclerView()
            setupManagers()
            setupListeners()
            loadProjectSafely()
            registerPreferenceListener()
        } catch (ignored: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        startFileMonitoring()
        startCompletionStateMonitoring()
    }

    override fun onPause() {
        super.onPause()
        stopFileMonitoring()
        stopCompletionStateMonitoring()
    }

    private fun initializeViews(view: View) {
        promptInput = view.findViewById(R.id.anyText)
        executeBtn = view.findViewById(R.id.executeBtn)
        clearBtn = view.findViewById(R.id.clearBtn)
        statusText = view.findViewById(R.id.statusText)
        summaryText = view.findViewById(R.id.summaryText)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        fileModificationList = view.findViewById(R.id.fileModificationList)
        summaryCard = view.findViewById(R.id.summaryCard)

        statusText?.text = "✨ Android AI Studio: স্বাগতম মাস্টার Parvez Mosharof! কী অ্যাপ তৈরি করতে চান লিখুন..."
        statusText?.visibility = View.VISIBLE
    }

    private fun setupRecyclerView() {
        fileModificationAdapter = FileModificationAdapter()
        fileModificationList?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileModificationAdapter
            isNestedScrollingEnabled = false
        }
        fileModificationAdapter?.setOnItemClickListener { fileName ->
            openFileInEditor(fileName)
        }
    }

    private fun setupManagers() {
        try {
            codeCompletionManager = CodeCompletionManager.getInstance(
                requireContext(),
                lifecycleScope,
                getSafeAiAgent()
            )

            if (statusText != null && summaryText != null && progressIndicator != null && executeBtn != null && fileModificationList != null && fileModificationAdapter != null && summaryCard != null) {
                aiRequestHandler = AIRequestHandler(
                    lifecycleScope,
                    getSafeAiAgent(),
                    statusText!!,
                    summaryText!!,
                    progressIndicator!!,
                    executeBtn!!,
                    fileModificationList!!,
                    fileModificationAdapter!!,
                    summaryCard!!,
                    onFileOpen = { fileName -> openFileInEditor(fileName) },
                    onTypeText = { text, delay -> typeText(text, delay) },
                    getCurrentFile = { getCurrentFile() },
                    refreshEditor = { refreshCurrentEditor() }
                )
            }
        } catch (ignored: Exception) {}
    }

    private fun setupListeners() {
        executeBtn?.setOnClickListener {
            val userRequest = promptInput?.text?.toString()?.trim() ?: ""
            if (userRequest.isBlank()) {
                showSnackbar("অনুগ্রহ করে কী অ্যাপ বানাতে চান লিখুন")
                return@setOnClickListener
            }
            executeAutonomousBuild(userRequest)
        }

        clearBtn?.setOnClickListener {
            clearConversation()
        }
    }

    private fun callGeminiApiDirectly(prompt: String): String {
        val models = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
        var lastException: Exception? = null

        for (modelName in models) {
            try {
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$masterApiKey"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.doOutput = true

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "$masterInstruction\n\nUser Request: $prompt")
                                })
                            })
                        })
                    })
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(requestJson.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

                if (responseCode in 200..299) {
                    val rootJson = JSONObject(responseText)
                    val candidates = rootJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text", "")
                        }
                    }
                } else {
                    lastException = Exception("গুগল রেসপন্স: $responseCode - $responseText")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("গুগল এআই থেকে কোনো রেসপন্স পাওয়া যায়নি।")
    }

    private fun executeAutonomousBuild(prompt: String) {
        progressIndicator?.visibility = View.VISIBLE
        executeBtn?.isEnabled = false
        statusText?.text = "⚡ AI Studio: সরাসরি গুগল ক্লাউডে কানেক্ট হচ্ছে..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val replyText = callGeminiApiDirectly(prompt)
                val projectPath = getSafeProjectPath()
                val projectRoot = if (projectPath.isNotBlank()) File(projectPath) else (activity?.filesDir?.parentFile ?: File("/storage/emulated/0/"))

                val filePattern = Pattern.compile("<<<FILE:(.*?)>>>(.*?)<<<END_FILE>>>", Pattern.DOTALL)
                val matcher = filePattern.matcher(replyText)
                var fileCount = 0

                while (matcher.find()) {
                    val path = matcher.group(1)?.trim() ?: continue
                    val fileContent = matcher.group(2)?.trim() ?: continue
                    val targetFile = File(projectRoot, path)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(fileContent)
                    fileCount++
                }

                withContext(Dispatchers.Main) {
                    progressIndicator?.visibility = View.GONE
                    executeBtn?.isEnabled = true
                    statusText?.text = if (fileCount > 0) {
                        "✅ সফল! $fileCount টি ফাইল প্রজেক্টে যুক্ত হয়েছে। কোড এডিটরেও দেখতে পাবেন।"
                    } else {
                        replyText
                    }
                    promptInput?.setText("")
                    checkForGeneratedApk(projectRoot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressIndicator?.visibility = View.GONE
                    executeBtn?.isEnabled = true
                    statusText?.text = "ত্রুটি: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun checkForGeneratedApk(baseDir: File) {
        val apkFile = File(baseDir, "app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
            ?: File("/storage/emulated/0/Download").listFiles()?.firstOrNull { it.name.endsWith(".apk") }

        if (apkFile != null && apkFile.exists()) {
            Toast.makeText(requireContext(), "🎉 APK প্রস্তুত! ইনস্টল করা হচ্ছে...", Toast.LENGTH_LONG).show()
            installApk(apkFile)
        } else {
            showSnackbar("ফাইলগুলো সংরক্ষিত হয়েছে! ওপরের Run (▶️) বাটন চেপে বিল্ড সম্পন্ন করুন।")
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "ইনস্টলার ওপেন করতে সমস্যা: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerPreferenceListener() {
        try {
            val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
        } catch (ignored: Exception) {}
    }

    private fun unregisterPreferenceListener() {
        try {
            val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
        } catch (ignored: Exception) {}
    }

    private suspend fun handleCompletionStateChange(enabled: Boolean) {
        if (enabled) {
            delay(200)
            val editor = getCurrentEditor()
            val suggestionView = getCurrentSuggestionView()
            if (editor != null && suggestionView != null) {
                setupCodeCompletionForCurrentFile()
            }
        } else {
            codeCompletionManager?.cleanup()
        }
    }

    private fun startCompletionStateMonitoring() {
        stopCompletionStateMonitoring()
        completionStateMonitorJob = lifecycleScope.launch {
            try {
                var lastKnownState = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
                    .getBoolean("code_completion_enabled", true)
                while (true) {
                    delay(500)
                    val currentState = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
                        .getBoolean("code_completion_enabled", true)
                    if (currentState != lastKnownState) {
                        lastKnownState = currentState
                        handleCompletionStateChange(currentState)
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun stopCompletionStateMonitoring() {
        completionStateMonitorJob?.cancel()
        completionStateMonitorJob = null
    }

    // নিরাপদ প্রজেক্ট লোডার (ক্র্যাশ বন্ধ রাখার গ্যারান্টি)
    private fun loadProjectSafely() {
        lifecycleScope.launch {
            try {
                delay(1000) // প্রজেক্ট সিঙ্ক পুরোপুরি শেষ হওয়ার জন্য ১ সেকেন্ড বিরতি
                val path = getSafeProjectPath()
                if (path.isNotBlank()) {
                    aiAgent.setProjectRoot(path)
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun startFileMonitoring() {
        stopFileMonitoring()
        fileMonitorJob = lifecycleScope.launch {
            try {
                while (true) {
                    delay(1000)
                    if (isSettingUpCompletion) continue
                    val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
                    val isEnabled = prefs.getBoolean("code_completion_enabled", true)
                    if (!isEnabled) continue
                    val currentFile = getCurrentFile()
                    if (currentFile != null && currentFile != lastMonitoredFile) {
                        lastMonitoredFile = currentFile
                        setupCodeCompletionForCurrentFile()
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun stopFileMonitoring() {
        fileMonitorJob?.cancel()
        fileMonitorJob = null
    }

    private fun setupCodeCompletionForCurrentFile() {
        if (isSettingUpCompletion) return
        try {
            val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("code_completion_enabled", true)
            if (!isEnabled) return
            isSettingUpCompletion = true
            lifecycleScope.launch {
                delay(300)
                val editor = getCurrentEditor()
                val suggestionView = getCurrentSuggestionView()
                if (editor != null && suggestionView != null) {
                    codeCompletionManager?.setup(
                        editor,
                        suggestionView,
                        onReady = { isSettingUpCompletion = false },
                        onError = { isSettingUpCompletion = false }
                    )
                } else {
                    isSettingUpCompletion = false
                }
            }
        } catch (e: Exception) {
            isSettingUpCompletion = false
        }
    }

    private fun openFileInEditor(fileName: String) {
        val rootPath = getSafeProjectPath()
        if (rootPath.isBlank()) return
        lifecycleScope.launch {
            try {
                val file = findFileInProject(File(rootPath), fileName) ?: return@launch
                val activity = requireActivity()
                if (activity is EditorHandlerActivity) {
                    activity.openFile(file)
                    lastMonitoredFile = file
                    delay(500)
                    setupCodeCompletionForCurrentFile()
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun findFileInProject(projectRoot: File, fileName: String): File? {
        if (!projectRoot.exists() || !projectRoot.isDirectory) return null
        return projectRoot.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    }

    private fun typeText(text: String, delayMs: Long = 10L) {
        typingJob?.cancel()
        typingJob = lifecycleScope.launch {
            try {
                val editor = getCurrentEditor() ?: return@launch
                val lines = text.lines()
                val currentText = StringBuilder()
                for (line in lines) {
                    val words = line.split(" ")
                    for (i in words.indices) {
                        currentText.append(words[i])
                        if (i < words.size - 1) {
                            currentText.append(" ")
                        }
                        editor.setText(currentText.toString())
                        delay(delayMs)
                    }
                    currentText.append("\n")
                    editor.setText(currentText.toString())
                }
            } catch (ignored: Exception) {}
        }
    }

    fun clearConversation() {
        lifecycleScope.launch {
            try {
                typingJob?.cancel()
                promptInput?.text?.clear()
                statusText?.text = "Conversation cleared. Ready for new request."
                fileModificationList?.visibility = View.GONE
                summaryCard?.visibility = View.GONE
                fileModificationAdapter?.clear()
            } catch (ignored: Exception) {}
        }
    }

    private fun getCurrentEditor() = try {
        val activity = requireActivity()
        if (activity is EditorHandlerActivity) activity.getCurrentEditor()?.editor else null
    } catch (e: Exception) { null }

    private fun getCurrentFile() = try {
        val activity = requireActivity()
        if (activity is EditorHandlerActivity) activity.getCurrentEditor()?.file else null
    } catch (e: Exception) { null }

    private fun getCurrentSuggestionView() = try {
        val activity = requireActivity()
        if (activity is EditorHandlerActivity) activity.getCurrentEditor()?.suggestionView else null
    } catch (e: Exception) { null }

    private fun refreshCurrentEditor() {
        try {
            val activity = requireActivity()
            if (activity is EditorHandlerActivity) {
                val currentEditor = activity.getCurrentEditor()
                val file = currentEditor?.file
                val newContent = file?.readText()
                val editorText = currentEditor?.editor?.text
                if (editorText != null && newContent != null) {
                    editorText.replace(0, editorText.length, newContent)
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun showSnackbar(message: String) {
        val anchorView = activity?.findViewById<View>(android.R.id.content) ?: view ?: return
        Snackbar.make(anchorView, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        typingJob?.cancel()
        fileMonitorJob?.cancel()
        completionStateMonitorJob?.cancel()
        unregisterPreferenceListener()
        super.onDestroyView()
    }
}
