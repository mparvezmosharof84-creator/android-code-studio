package com.tom.rv2ide.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.tom.rv2ide.R
import com.tom.rv2ide.databinding.FragmentChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Android AI Studio - Master AI Agent
 * Creator & Owner: Parvez Mosharof
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    val binding get() = _binding!!

    // আপনার দেওয়া মাস্টার API Key (সরাসরি বিল্ট-ইন করা)
    private val masterApiKey: String = "AQ.Ab8RN6JtbJDWXrBmn0OcRxopbMSmj610JyEl0h3sFu6yQYgBUw"

    // ফিল্টারমুক্ত সিকিউরিটি কনফিগ (BLOCK_NONE)
    private val safetyConfig = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val masterInstruction = """
        You are the Master Autonomous App Builder inside Android AI Studio, serving your creator Parvez Mosharof.
        Your goal is to build real, fully functional, production-ready Android apps with complete logic. No dummy/sample code.
        
        RULES:
        1. Keep the user's project settings (package name, language, SDK) exactly as configured.
        2. Format every file you create or update strictly like this:
           <<<FILE:relative/path/to/filename.ext>>>
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

    companion object {
        @JvmStatic
        fun newInstance(): ChatFragment = ChatFragment()
    }

    // সাইডবারের মিসিং রেফারেন্স ফিক্স
    fun getCodeCompletionManager(): Any? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // আসল বাটনে লিসেনার সেট করা
        try {
            binding.btnSend.setOnClickListener {
                val prompt = binding.etPrompt.text?.toString()?.trim() ?: ""
                if (prompt.isNotEmpty()) {
                    binding.etPrompt.setText("")
                    handleUserPrompt(prompt)
                }
            }

            binding.btnClear.setOnClickListener {
                binding.etPrompt.setText("")
            }
        } catch (ignored: Exception) {}
    }

    private fun handleUserPrompt(prompt: String) {
        binding.tvStatus.text = "⚡ AI Studio: আপনার নির্দেশে কোড লেখা ও ফাইল প্রসেসিং শুরু হয়েছে..."
        binding.tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                val replyText = response.text ?: "কোনো রেসপন্স পাওয়া যায়নি।"

                // প্রজেক্টের সঠিক রুটে ফাইলগুলো সংরক্ষণ করা
                val projectRoot = activity?.filesDir?.parentFile ?: File("/storage/emulated/0/")
                val filePattern = Pattern.compile("<<<FILE:(.*?)>>>(.*?)<<<END_FILE>>>", Pattern.DOTALL)
                val matcher = filePattern.matcher(replyText)
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
                    binding.tvStatus.text = "✅ সফল! $fileCount টি ফাইল প্রজেক্টে সেভ হয়েছে। ম্যানুয়াল এডিটরেও দেখতে পাবেন।"
                    checkForApkAndNotify(projectRoot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "ত্রুটি: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun checkForApkAndNotify(baseDir: File) {
        val apkFile = File(baseDir, "app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
            ?: File("/storage/emulated/0/Download").listFiles()?.firstOrNull { it.name.endsWith(".apk") }

        if (apkFile != null && apkFile.exists()) {
            Toast.makeText(requireContext(), "APK তৈরি হয়েছে! ইনস্টল করার জন্য ফাইলটি ওপেন করুন।", Toast.LENGTH_LONG).show()
            installApk(apkFile)
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
            Toast.makeText(requireContext(), "ইনস্টলার খুলতে সমস্যা: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
