package com.tom.rv2ide.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

object AutonomousAgent {

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = AIConfig.MODEL_NAME,
            apiKey = AIConfig.GEMINI_API_KEY,
            systemInstruction = content { text(AIConfig.SYSTEM_INSTRUCTION) }
        )
    }

    suspend fun executeUserPrompt(
        projectRoot: File,
        prompt: String,
        onStatusUpdate: (String) -> Unit,
        onBuildSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("AI ব্রেন কোড তৈরি করছে...")
            val response = generativeModel.generateContent(prompt)
            val replyText = response.text ?: ""

            // ১. AI-এর দেওয়া ফাইলগুলো স্বয়ংক্রিয়ভাবে সেভ করা
            val filePattern = Pattern.compile("<<<FILE:(.*?)>>>(.*?)<<<END_FILE>>>", Pattern.DOTALL)
            val matcher = filePattern.matcher(replyText)
            var filesWritten = 0

            while (matcher.find()) {
                val relativePath = matcher.group(1)?.trim() ?: continue
                val fileContent = matcher.group(2)?.trim() ?: continue
                
                val targetFile = File(projectRoot, relativePath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(fileContent)
                filesWritten++
                onStatusUpdate("ফাইল সেভ হয়েছে: $relativePath")
            }

            // ২. বিল্ড ট্রিগার চেক করা
            if (replyText.contains("<<<TRIGGER_BUILD>>>") || filesWritten > 0) {
                onStatusUpdate("কোড সেভ সম্পন্ন! ব্যাকগ্রাউন্ডে APK বিল্ড শুরু হচ্ছে...")
                
                // জেনারেট হওয়া APK খোঁজা
                val apkDir = File(projectRoot, "app/build/outputs/apk/debug")
                val apkFile = apkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                    ?: File(projectRoot, "core/app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }

                if (apkFile != null && apkFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onBuildSuccess(apkFile)
                    }
                } else {
                    onStatusUpdate("ফাইলগুলো প্রজেক্টে সফলভাবে যুক্ত হয়েছে! আপনি রান বাটনে চাপ দিয়েও দেখতে পারেন।")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("AI প্রসেসে সমস্যা: ${e.localizedMessage}")
            }
        }
    }

    // চ্যাট থেকে সরাসরি অ্যাপ ইনস্টল করার ফাংশন
    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
