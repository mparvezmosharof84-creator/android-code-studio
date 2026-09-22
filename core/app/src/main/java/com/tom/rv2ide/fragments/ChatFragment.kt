package com.tom.rv2ide.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
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
 * Android AI Studio - Autonomous AI App Builder
 * Owner & Creator: Parvez Mosharof
 */
class ChatFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(): ChatFragment = ChatFragment()
    }

    // কোড কমপ্লিশন সাপোর্টের জন্য ডামি ম্যানেজার
    fun getCodeCompletionManager(): Any? = null

    // আপনার ব্যক্তিগত মাস্টার API Key (সরাসরি বিল্ট-ইন)
    private val masterApiKey = "AQ.Ab8RN6JtbJDWXrBmn0OcRxopbMSmj610JyEl0h3sFu6yQYgBUw"

    // ফিল্টারমুক্ত সিকিউরিটি কনফিগ (BLOCK_NONE)
    private val safetyConfig = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val masterInstruction = """
        You are the Master Autonomous App Builder inside Android AI Studio, created for Parvez Mosharof.
        Your mission is to build real, fully functional Android apps with production-grade logic. No placeholders, no TODOs.
        
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

        // সরাসরি ডায়নামিক UI তৈরি (কোনো মিসিং R.id ক্র্যাশ হবে না)
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF181A1F.toInt())
            setPadding(20, 20, 20, 20)
        }

        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
            )
        }

        chatContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(chatContainer)
        mainLayout.addView(scrollView)

        progressBar = ProgressBar(context).apply {
            visibility = View.GONE
        }
        mainLayout.addView(progressBar)

        val inputBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        etPrompt = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            hint = "কী অ্যাপ বানাতে চান লিখুন..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF21252B.toInt())
            setPadding(25, 20, 25, 20)
        }
        inputBar.addView(etPrompt)

        btnSend = Button(context).apply {
            text = "Send"
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val userText = etPrompt.text.toString().trim()
                if (userText.isNotEmpty()) {
                    addMessageToChat("User: $userText", false)
                    etPrompt.setText("")
                    processAppBuilding(userText)
                }
            }
        }
        inputBar.addView(btnSend)
        mainLayout.addView(inputBar)

        addMessageToChat("👋 স্বাগতম মাস্টার Parvez Mosharof! আমি আপনার Android AI Studio বিল্ডার। আপনি কী অ্যাপ বা ফিচার তৈরি করতে চান বলুন, আমি রিয়েল কোড লিখে প্রজেক্টে বসিয়ে দিচ্ছি।", true)

        return mainLayout
    }

    private fun processAppBuilding(prompt: String) {
        progressBar.visibility = View.VISIBLE
        addMessageToChat("⚡ AI Studio: ফাইল জেনারেট ও প্রজেক্ট কোডিং শুরু হয়েছে...", true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                val reply = response.text ?: "কোনো রেসপন্স পাওয়া যায়নি।"

                val projectRoot = activity?.filesDir?.parentFile ?: File("/storage/emulated/0/")
                val filePattern = Pattern.compile("<<<FILE:(.*?)>>>(.*?)<<<END_FILE>>>", Pattern.DOTALL)
                val matcher = filePattern.matcher(reply)
                var fileCount = 0

                while (matcher.find()) {
                    val path = matcher.group(1)?.trim() ?: continue
                    val code = matcher.group(2)?.trim() ?: continue
                    val target = File(projectRoot, path)
                    target.parentFile?.mkdirs()
                    target.writeText(code)
                    fileCount++
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    addMessageToChat("✅ $fileCount টি ফাইল সরাসরি প্রজেক্টে যুক্ত হয়েছে! কোড এডিটরেও দেখতে পাবেন।", true)
                    displayAppActionCard(projectRoot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    addMessageToChat("ত্রুটি: ${e.localizedMessage}", true)
                }
            }
        }
    }

    private fun displayAppActionCard(projectDir: File) {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            setBackgroundColor(0xFF282C34.toInt())
        }

        val tvTitle = TextView(requireContext()).apply {
            text = "🎉 App Ready! নিচের বাটন থেকে অ্যাকশন নিন:"
            textSize = 15f
            setTextColor(0xFF4CAF50.toInt())
        }
        card.addView(tvTitle)

        val btnInstall = Button(requireContext()).apply {
            text = "🚀 Install APK"
            setBackgroundColor(0xFF2E7D32.toInt())
            setOnClickListener {
                findAndInstallApk(projectDir)
            }
        }
        card.addView(btnInstall)

        val btnDownloadAab = Button(requireContext()).apply {
            text = "📦 Download / Share AAB Bundle"
            setBackgroundColor(0xFF1565C0.toInt())
            setOnClickListener {
                findAndShareAab(projectDir)
            }
        }
        card.addView(btnDownloadAab)

        chatContainer.addView(card)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun findAndInstallApk(baseDir: File) {
        val apkFile = File(baseDir, "app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
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
            addMessageToChat("APK ফাইলটি পাওয়ার জন্য ওপরের Run (▶️) বাটনে চাপ দিয়ে একবার বিল্ড সম্পন্ন করুন।", true)
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
            addMessageToChat("AAB বান্ডেল ফাইল পাওয়া যায়নি। প্রোজেক্ট থেকে Bundle জেনারেট করে নিন।", true)
        }
    }

    private fun addMessageToChat(message: String, isAi: Boolean) {
        val tv = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            setPadding(20, 15, 20, 15)
            setTextColor(if (isAi) 0xFF81C784.toInt() else 0xFFFFFFFF.toInt())
        }
        chatContainer.addView(tv)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
}
