package com.tom.rv2ide.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Android AI Studio - Google AI Studio Style App Builder
 * Master Creator & Owner: Parvez Mosharof
 */
class ChatFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(): ChatFragment = ChatFragment()
    }

    // সাইডবার ও অ্যাডাপ্টারের কম্প্যাটিবিলিটি মেথড (বিল্ড এরর ঠেকানোর জন্য)
    fun getCodeCompletionManager(): Any? = null

    // বিল্ট-ইন মাস্টার API Key
    private val masterApiKey = "AQ.Ab8RN6JtbJDWXrBmn0OcRxopbMSmj610JyEl0h3sFu6yQYgBUw"

    // ফিল্টারমুক্ত সিকিউরিটি কনফিগ (BLOCK_NONE)
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
        1. Keep the user's project settings (package name, language, SDK) exactly as configured.
        2. Format every file strictly like this:
           <<<FILE:relative/path/to/file.ext>>>
           [Complete code here]
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

    private lateinit var etPrompt: EditText
    private lateinit var btnSend: Button
    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        // Google AI Studio ডার্ক ব্যাকগ্রাউন্ড লেআউট (#131314)
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#131314"))
            setPadding(24, 24, 24, 24)
        }

        // টপ হেডার বার
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvHeader = TextView(context).apply {
            text = "✨ Android AI Studio  •  Autonomous Builder"
            setTextColor(Color.parseColor("#E3E3E3"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerLayout.addView(tvHeader)
        rootLayout.addView(headerLayout)

        // চ্যাট স্ক্রোলভিউ
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
            )
            isVerticalScrollBarEnabled = false
        }

        chatContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(chatContainer)
        rootLayout.addView(scrollView)

        // প্রগ্রেস বার
        progressBar = ProgressBar(context).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = lp
        }
        rootLayout.addView(progressBar)

        // আধুনিক গুগল এআই স্টুডিও ইনপুট বার
        val inputContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F20"))
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#333538"))
            }
        }

        etPrompt = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            hint = "কী অ্যাপ বানাতে চান লিখুন (যেমন: ক্যালকুলেটর, নোটপ্যাড)..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8E918F"))
            background = null
            setPadding(24, 18, 24, 18)
            textSize = 14f
        }
        inputContainer.addView(etPrompt)

        btnSend = Button(context).apply {
            text = "Generate"
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#A8C7FA"))
                cornerRadius = 24f
            }
            setPadding(32, 12, 32, 12)
            setOnClickListener {
                val promptText = etPrompt.text.toString().trim()
                if (promptText.isNotEmpty()) {
                    addMessageCard("User", promptText, false)
                    etPrompt.setText("")
                    startAutonomousBuild(promptText)
                }
            }
        }
        inputContainer.addView(btnSend)
        rootLayout.addView(inputContainer)

        // ওয়েলকাম মেসেজ
        addMessageCard(
            "Android AI Studio",
            "👋 স্বাগতম মাস্টার Parvez Mosharof!\nআমি আপনার ব্যক্তিগত Autonomous App Builder। আপনি শুধু বলুন কী অ্যাপ বানাতে চান—আমি স্বয়ংক্রিয়ভাবে রিয়েল কোড লিখে প্রজেক্টের ফাইলে সেভ করব এবং নিচে সরাসরি ইনস্টল বাটন তুলে দেব।",
            true
        )

        return rootLayout
    }

    private fun startAutonomousBuild(prompt: String) {
        progressBar.visibility = View.VISIBLE
        addMessageCard("Status", "⚡ প্রজেক্ট বিশ্লেষণ ও ফাইল জেনারেশন চলছে...", true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                val replyText = response.text ?: "কোনো কোড পাওয়া যায়নি।"

                // প্রজেক্ট রুট ফোল্ডার চিহ্নিত করা
                val projectRoot = findActiveProjectRoot()
                val filePattern = Pattern.compile("<<<FILE:(.*?)>>>(.*?)<<<END_FILE>>>", Pattern.DOTALL)
                val matcher = filePattern.matcher(replyText)
                var count = 0

                while (matcher.find()) {
                    val relativePath = matcher.group(1)?.trim() ?: continue
                    val fileData = matcher.group(2)?.trim() ?: continue
                    val targetFile = File(projectRoot, relativePath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(fileData)
                    count++
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    addMessageCard("Success", "✅ $count টি ফাইল প্রজেক্টে সফলভাবে যুক্ত হয়েছে! কোড এডিটরেও সরাসরি দেখতে পাবেন।", true)
                    displayActionCard(projectRoot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    addMessageCard("Error", "ত্রুটি: ${e.localizedMessage}", true)
                }
            }
        }
    }

    private fun displayActionCard(projectDir: File) {
        val context = requireContext()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F20"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#00E676"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
        }

        val tvTitle = TextView(context).apply {
            text = "🎉 App Ready! নিচের বাটন থেকে অ্যাকশন নিন:"
            textSize = 15f
            setTextColor(Color.parseColor("#00E676"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        }
        card.addView(tvTitle)

        // Install APK বাটন
        val btnInstall = Button(context).apply {
            text = "🚀 Install APK"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E7D32"))
                cornerRadius = 18f
            }
            setPadding(0, 24, 0, 24)
            setOnClickListener {
                findAndInstallApk(projectDir)
            }
        }
        card.addView(btnInstall)

        // Download / Share AAB বাটন
        val btnAab = Button(context).apply {
            text = "📦 Download / Share AAB Bundle"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1565C0"))
                cornerRadius = 18f
            }
            setPadding(0, 24, 0, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
            layoutParams = lp
            setOnClickListener {
                findAndShareAab(projectDir)
            }
        }
        card.addView(btnAab)

        chatContainer.addView(card)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun findAndInstallApk(baseDir: File) {
        val apkFile = File(baseDir, "app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
            ?: File(baseDir, "core/app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
            ?: File("/storage/emulated/0/Download").listFiles()?.firstOrNull { it.name.endsWith(".apk") }

        if (apkFile != null && apkFile.exists()) {
            val uri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "APK তৈরি হচ্ছে। ওপরের Run (▶️) বাটন চেপে একবার বিল্ড করে নিন।", Toast.LENGTH_LONG).show()
        }
    }

    private fun findAndShareAab(baseDir: File) {
        val aabFile = File(baseDir, "app/build/outputs/bundle/release").listFiles()?.firstOrNull { it.name.endsWith(".aab") }
        if (aabFile != null && aabFile.exists()) {
            val uri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", aabFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share AAB Bundle"))
        } else {
            Toast.makeText(requireContext(), "AAB ফাইল পাওয়া যায়নি। প্রোজেক্ট থেকে Bundle রান করুন।", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMessageCard(sender: String, message: String, isAi: Boolean) {
        val context = requireContext()
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
            background = GradientDrawable().apply {
                setColor(if (isAi) Color.parseColor("#1E1F20") else Color.parseColor("#282A2C"))
                cornerRadius = 20f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) }
            layoutParams = lp
        }

        val tvSender = TextView(context).apply {
            text = sender
            textSize = 12f
            setTextColor(if (isAi) Color.parseColor("#A8C7FA") else Color.parseColor("#C4C7C5"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 6)
        }
        bubble.addView(tvSender)

        val tvMsg = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(6f, 1.1f)
        }
        bubble.addView(tvMsg)

        chatContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun findActiveProjectRoot(): File {
        val defaultPaths = listOf(
            File("/storage/emulated/0/AndroidIDEProjects"),
            File("/storage/emulated/0/AndroidCodeStudioProjects"),
            File("/storage/emulated/0/android-code-studio-projects")
        )
        for (dir in defaultPaths) {
            if (dir.exists()) {
                val latest = dir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.lastModified() }
                if (latest != null) return latest
            }
        }
        return activity?.filesDir?.parentFile ?: File("/storage/emulated/0/")
    }
}
