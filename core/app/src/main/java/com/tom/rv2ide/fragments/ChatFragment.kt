package com.tom.rv2ide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tom.rv2ide.R

class ChatFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(): ChatFragment = ChatFragment()
    }

    fun getCodeCompletionManager(): Any? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }
}
