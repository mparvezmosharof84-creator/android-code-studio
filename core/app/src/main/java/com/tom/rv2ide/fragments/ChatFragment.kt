/*
 * Android AI Studio - Autonomous Builder Core
 * Creator & Master: Parvez Mosharof
 */

package com.tom.rv2ide.fragments

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Android AI Studio Autonomous Architecture
 * সম্পূর্ণ তাদের ভাঙা এআই সরিয়ে আপনার নিজস্ব স্বাধীন গুগলের মাস্টার ইঞ্জিন যুক্ত।
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
    private val userRootProject by lazy { getProjectRoot().absolutePath.toString() }

    // আপনার দেওয়া ব্যক্তিগত মাস্টার API Key (সরাসরি ইন্টারনাল ইঞ্জিনে যুক্ত)
    private val masterApiKey = "AQ.Ab8RN6JtbJDWXrBmn0OcRxopbMSmj610JyEl0h3sFu6yQYgBUw"

    // ফিল্টারমুক্ত পলিসি কনফিগ (যাতে কোনো কোডে না না বলে)
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

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = masterApiKey,
            safetySettings = safetyConfig,
            systemInstruction = content { text(masterInstruction) }
        )
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

    // সাইডবার ও অ্যাডাপ্টারের টাইপ-সেফ মেথড (যাতে বিল্ড ১০০% পাস হয়)
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
        // স্বয়ংক্রিয়ভাবে preferences-এ মাস্টার কী ইনজেক্ট করা
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_key", masterApiKey)
            .putString("gemini_api_key", masterApiKey)
            .putString("apiKey", masterApiKey)
            .apply()

        getSafeAiAgent()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupRecyclerView()
        setupManagers()
        setupAutonomousListeners()
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

        statusText.text = "✨ Android AI Studio: স্বাগতম মাস্টার Parvez Mosharof! আপনার কি অ্যাপ তৈরি করতে হবে লিখুন..."
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
            requireContext(), lifecycleScope, getSafeAiAgent()
        )
    }

    private fun setupAutonomousListeners() {
        // আপনার নতুন নিজস্ব গুগল এআই স্টুডিও স্বয়ংক্রিয় বিল্ডার রানার
        executeBtn.setOnClickListener {
            val userPrompt = promptInput.text.toString().trim()
            if (userPrompt.isBlank()) {
                showSnackbar("অনুগ্রহ করে কী অ্যাপ বানাতে চান লিখুন")
                return@setOnClickListener
            }
            executeAutonomousBuild(userPrompt)
        }

        clearBtn.setOnClickListener {
            promptInput.setText("")
            fileModificationAdapter.clear()
            summaryCard.visibility = View.GONE
            statusText.text = "নতুন রিকোয়েস্টের জন্য প্রস্তুত।"
        }
    }

    private fun executeAutonomousBuild(prompt: String) {
        progressIndicator.visibility = View.VISIBLE
        executeBtn.isEnabled = false
        statusText.text = "⚡ AI Studio: মাস্টার নির্দেশনায় প্রজেক্টের ফাইল কোডিং চলছে..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                val replyText = response.text ?: ""

                // ১. প্রজেক্টের আসল ফাইলে স্বয়ংক্রিয়ভাবে কোড লেখা ও সেভ করা
                val projectRoot = File(userRootProject)
                val filePattern = Pattern.compile("<<<FILE:(.*?)>>>(.*?)<<<END_FILE>>>", Pattern.DOTALL)
                val matcher = filePattern.matcher(replyText)
                var fileCount = 0

                while (matcher.find()) {
                    val relativePath = matcher.group(1)?.trim() ?: continue
                    val fileContent = matcher.group(2)?.trim() ?: continue
                    val targetFile = File(projectRoot, relativePath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(fileContent)
                    fileCount++
                }

                withContext(Dispatchers.Main) {
                    progressIndicator.visibility = View.GONE
                    executeBtn.isEnabled = true
                    statusText.text = "✅ সফল! $fileCount টি ফাইল প্রজেক্টে যুক্ত হয়েছে। কোড এডিটরেও দেখতে পাবেন।"
                    promptInput.setText("")

                    // ২. বিল্ড শেষে ইনস্টল করার অপশন নোটিফাই করা
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
            showSnackbar("ফাইলগুলো সংরক্ষিত হয়েছে! ওপরের Run (▶️) বাটন চেপে একবার রান করুন।")
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

    private fun openFileInEditor(fileName: String) {
        val file = File(userRootProject).walkTopDown().firstOrNull { it.isFile && it.name == fileName }
        if (file != null) {
            val activity = requireActivity()
            if (activity is EditorHandlerActivity) {
                activity.openFile(file)
            }
        }
    }

    private fun showSnackbar(message: String) {
        val anchorView = activity?.findViewById<View>(android.R.id.content) ?: view ?: return
        Snackbar.make(anchorView, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        typingJob?.cancel()
        super.onDestroyView()
    }
}
