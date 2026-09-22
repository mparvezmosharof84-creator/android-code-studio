package com.tom.rv2ide.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

object AutonomousAgent {

    // গুগলের সব ধরনের ফিল্টার সম্পূর্ণ বন্ধ রাখার কনফিগারেশন (BLOCK_NONE)
    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = AIConfig.MODEL_NAME,
            apiKey = AIConfig.GEMINI_API_KEY,
            safetySettings = safetySettings,
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
            onStatusUpdate("মাস্টারের নির্দেশনায় AI কোড তৈরি করছে...")
            val response = generativeModel.generateContent(prompt)
            val replyText = response.text ?: ""

            // ১. AI-এর দেওয়া ফাইলগুলো স্বয়ংক্রিয়ভাবে প্রজেক্টে সেভ করা
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
                onStatusUpdate("ফাইল তৈরি হয়েছে: $relativePath")
            }

            // ২. ব্যাকগ্রাউন্ডে APK বিল্ড ট্রিগার করা
            if (replyText.contains("<<<TRIGGER_BUILD>>>") || filesWritten > 0) {
                onStatusUpdate("কোড সংরক্ষণ সফল! APK বিল্ড শুরু হচ্ছে...")
                
                val apkDir = File(projectRoot, "app/build/outputs/apk/debug")
                val apkFile = apkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                    ?: File(projectRoot, "core/app/build/outputs/apk/debug").listFiles()?.firstOrNull { it.name.endsWith(".apk") }

                if (apkFile != null && apkFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onBuildSuccess(apkFile)
                    }
                } else {
                    onStatusUpdate("ফাইলগুলো প্রজেক্টে সংরক্ষিত হয়েছে। অ্যাপটি পরীক্ষা করতে রান দিতে পারেন।")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("AI প্রসেসে ত্রুটি: ${e.localizedMessage}")
            }
        }
    }

    // চ্যাট থেকেই ১ ক্লিকে সরাসরি অ্যাপ ইনস্টল করার ফাংশন
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
