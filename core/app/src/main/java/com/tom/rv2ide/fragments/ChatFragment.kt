package com.tom.rv2ide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tom.rv2ide.managers.CodeCompletionManager

/**
 * Android AI Studio - Dedicated AI Workspace
 * Owner & Creator: Parvez Mosharof
 */
class ChatFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(aiAgent: Any? = null): ChatFragment = ChatFragment()
        @JvmStatic
        fun newInstance(): ChatFragment = ChatFragment()
    }

    fun getCodeCompletionManager(): CodeCompletionManager? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return TextView(requireContext()).apply {
            text = "✨ Android AI Studio Workspace\n\nCreator: Parvez Mosharof\nস্বাগতম মাস্টার! আপনার নতুন কাস্টম AI ইঞ্জিন সংযুক্তির জন্য সিস্টেম প্রস্তুত।"
            textSize = 16f
            setPadding(40, 60, 40, 40)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF131314.toInt())
        }
    }
}
