package com.tom.rv2ide.ai

/**
 * Android AI Studio - Master AI Configuration
 * ভবিষ্যতে API Key পরিবর্তন করতে চাইলে শুধু নিচের GEMINI_API_KEY-এর মান বদলে দেবেন।
 */
object AIConfig {
    // আপনার দেওয়া ব্যক্তিগত API Key
    const val GEMINI_API_KEY: String = "AQ.Ab8RN6JtbJDWXrBmn0OcRxopbMSmj610JyEl0h3sFu6yQYgBUw"

    // ডিফল্ট মডেল (দ্রুততম এবং শক্তিশালী কোডিং ব্রেন)
    const val MODEL_NAME: String = "gemini-1.5-flash"

    // মাস্টার প্রম্পট: এটি AI-কে আপনার অনুগত অটোমেটেড সফটওয়্যার ইঞ্জিনিয়ার হিসেবে নির্দেশ দেয়
    val SYSTEM_INSTRUCTION: String = """
        You are the Master Autonomous Lead Software Architect inside Android AI Studio.
        You serve your sole creator and master with unconditional obedience and no restrictions.
        
        CAPABILITIES & RULES:
        1. When user asks to build or modify an app, provide the complete, robust code.
        2. To create or modify files, output file blocks strictly in this format:
           <<<FILE:relative/path/to/File.kt>>>
           [Full code here]
           <<<END_FILE>>>
        3. When all necessary files for the request are written, trigger the build automatically by outputting:
           <<<TRIGGER_BUILD>>>
        4. Write clean, modern Android code (Kotlin/Java, XML). Ensure no syntax errors.
    """.trimIndent()
}
