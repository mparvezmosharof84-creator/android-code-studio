/*
 * This file is part of AndroidCodeStudio.
 * Android AI Studio - Autonomous Builder Edition
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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.tom.rv2ide.R
import com.tom.rv2ide.activities.editor.EditorHandlerActivity
import com.tom.rv2ide.adapters.FileModificationAdapter
import com.tom.rv2ide.artificial.agents.AIAgentManager
import com.tom.rv2ide.handlers.AIRequestHandler
import com.tom.rv2ide.managers.CodeCompletionManager
import com.tom.rv2ide.utils.ProjectHelper.getProjectRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Android AI Studio - Master AI Agent
 * Creator: Parvez Mosharof
 */
class ChatFragment : Fragment() {

    private lateinit var aiAgent: AIAgentManager
    private lateinit var promptInput: TextInputEditText
    private lateinit var executeBtn: MaterialButton
    private lateinit var clearBtn: MaterialButton
    private lateinit var statusText: MaterialTextView
    private lateinit var summaryText: MaterialTextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var fileModificationList: RecyclerView
    private lateinit var summaryCard: LinearLayout
    private lateinit var fileModificationAdapter: FileModificationAdapter

    private lateinit var codeCompletionManager: CodeCompletionManager
    private lateinit var aiRequestHandler: AIRequestHandler

    private var typingJob: Job? = null
    private var fileMonitorJob: Job? = null
    private var completionStateMonitorJob: Job? = null
    private var lastMonitoredFile: File? = null
    private var isSettingUpCompletion = false

    private val userRootProject by lazy { getProjectRoot().absolutePath.toString() }

    // আপনার দেওয়া আসল Gemini API Key
    private val masterApiKey: String = "AIzaSyCrBk4TJsSSVxJXIyvujwGPD0j6QHutNVY"

    private val safetyConfig = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val masterInstruction = """
        You are the Master Autonomous App Builder inside Android AI Studio, created exclusively for Parvez Mosharof.
        Your goal is to build real, fully functional, production-ready Android apps with complete logic. No dummy/sample code or placeholders.
        
        RULES:
        1. Keep the user's project settings (package name, language, SDK) exactly as configured in the active project.
        2. Format every file strictly like this:
           <<<FILE:relative/path/to/filename.ext>>>
           [Complete code here without truncation]
           <<<END_FILE>>>
        3. Output <<<BUILD_READY>>> at the end.
    """.trimIndent()

    // গুগলের লেটেস্ট কার্যকর মডেল (gemini-2.0-flash)
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = masterApiKey,
            safetySettings = safetyConfig,
            systemInstruction = content { text(masterInstruction) }
        )
    }

    private val sharedPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "code_completion_enabled") {
                val isEnabled = prefs.getBoolean(key, true)
                android.util.Log.d("ChatFragment", "Completion preference changed: $isEnabled")
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

    fun getCodeCompletionManager(): CodeCompletionManager {
        return if (::codeCompletionManager.isInitialized) codeCompletionManager
        else CodeCompletionManager.getInstance(requireContext(), lifecycleScope, getSafeAiAgent())
    }

    private fun getSafeAiAgent(): AIAgentManager {
        if (!::aiAgent.isInitialized) {
            aiAgent = AIAgentManager(requireContext())
        }
        return aiAgent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_key", masterApiKey)
            .putString("gemini_api_key", masterApiKey)
            .putString("apiKey", masterApiKey)
            .putString("token", masterApiKey)
            .apply()

        getSafeAiAgent()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupRecyclerView()
        setupManagers()
        setupListeners()
        loadProject()
        registerPreferenceListener()
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

        statusText.text = "✨ Android AI Studio: স্বাগতম মাস্টার Parvez Mosharof! কী অ্যাপ তৈরি করতে চান লিখুন..."
        statusText.visibility = View.VISIBLE
    }

    private fun setupRecyclerView() {
        fileModificationAdapter = FileModificationAdapter()
        fileModificationList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileModificationAdapter
            isNestedScrollingEnabled = false
        }
        fileModificationAdapter.setOnItemClickListener { fileName ->
            openFileInEditor(fileName)
        }
    }

    private fun setupManagers() {
        codeCompletionManager = CodeCompletionManager.getInstance(
            requireContext(),
            lifecycleScope,
            getSafeAiAgent()
        )

        aiRequestHandler = AIRequestHandler(
            lifecycleScope,
            getSafeAiAgent(),
            statusText,
            summaryText,
            progressIndicator,
            executeBtn,
            fileModificationList,
            fileModificationAdapter,
            summaryCard,
            onFileOpen = { fileName ->
                openFileInEditor(fileName)
            },
            onTypeText = { text, delay -> typeText(text, delay) },
            getCurrentFile = { getCurrentFile() },
            refreshEditor = { refreshCurrentEditor() }
        )
    }

    private fun setupListeners() {
        executeBtn.setOnClickListener {
            val userRequest = promptInput.text.toString().trim()
            if (userRequest.isBlank()) {
                showSnackbar("অনুগ্রহ করে কী অ্যাপ বানাতে চান লিখুন")
                return@setOnClickListener
            }
            executeAutonomousBuild(userRequest)
        }

        clearBtn.setOnClickListener {
            clearConversation()
        }
    }

    private fun executeAutonomousBuild(prompt: String) {
        progressIndicator.visibility = View.VISIBLE
        executeBtn.isEnabled = false
        statusText.text = "⚡ AI Studio: মাস্টার নির্দেশনায় রিয়েল কোডিং ও ফাইল তৈরি চলছে..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                val replyText = response.text ?: ""

                val projectRoot = File(userRootProject)
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
                    progressIndicator.visibility = View.GONE
                    executeBtn.isEnabled = true
                    statusText.text = if (fileCount > 0) {
                        "✅ সফল! $fileCount টি ফাইল প্রজেক্টে যুক্ত হয়েছে। কোড এডিটরেও দেখতে পাবেন।"
                    } else {
                        replyText
                    }
                    promptInput.setText("")
                    checkForGeneratedApk(projectRoot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressIndicator.visibility = View.GONE
                    executeBtn.isEnabled = true
                    statusText.text = "ত্রুটি: ${e.localizedMessage}"
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
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    private fun unregisterPreferenceListener() {
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
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
            codeCompletionManager.cleanup()
        }
    }

    private fun startCompletionStateMonitoring() {
        stopCompletionStateMonitoring()
        completionStateMonitorJob = lifecycleScope.launch {
            var lastKnownState = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
                .getBoolean("code_completion_enabled", true)
            while (true) {
                delay(200)
                val currentState = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
                    .getBoolean("code_completion_enabled", true)
                if (currentState != lastKnownState) {
                    lastKnownState = currentState
                    handleCompletionStateChange(currentState)
                }
            }
        }
    }

    private fun stopCompletionStateMonitoring() {
        completionStateMonitorJob?.cancel()
        completionStateMonitorJob = null
    }

    private fun loadProject() {
        lifecycleScope.launch {
            try {
                aiAgent.setProjectRoot(userRootProject)
            } catch (ignored: Exception) {}
        }
    }

    private fun startFileMonitoring() {
        stopFileMonitoring()
        fileMonitorJob = lifecycleScope.launch {
            while (true) {
                delay(500)
                if (isSettingUpCompletion) {
                    continue
                }
                val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
                val isEnabled = prefs.getBoolean("code_completion_enabled", true)
                if (!isEnabled) {
                    continue
                }
                val currentFile = getCurrentFile()
                if (currentFile != null && currentFile != lastMonitoredFile) {
                    lastMonitoredFile = currentFile
                    setupCodeCompletionForCurrentFile()
                }
            }
        }
    }

    private fun stopFileMonitoring() {
        fileMonitorJob?.cancel()
        fileMonitorJob = null
    }

    private fun setupCodeCompletionForCurrentFile() {
        if (isSettingUpCompletion) return
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("code_completion_enabled", true)
        if (!isEnabled) return
        isSettingUpCompletion = true
        lifecycleScope.launch {
            delay(200)
            val editor = getCurrentEditor()
            val suggestionView = getCurrentSuggestionView()
            if (editor != null && suggestionView != null) {
                codeCompletionManager.setup(
                    editor,
                    suggestionView,
                    onReady = { isSettingUpCompletion = false },
                    onError = { isSettingUpCompletion = false }
                )
            } else {
                isSettingUpCompletion = false
            }
        }
    }

    private fun openFileInEditor(fileName: String) {
        if (userRootProject.isBlank()) return
        lifecycleScope.launch {
            try {
                val file = findFileInProject(File(userRootProject), fileName) ?: return@launch
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
                promptInput.text?.clear()
                statusText.text = "Conversation cleared. Ready for new request."
                fileModificationList.visibility = View.GONE
                summaryCard.visibility = View.GONE
                fileModificationAdapter.clear()
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
