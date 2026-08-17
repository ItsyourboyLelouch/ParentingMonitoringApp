package com.example.parentingmonitoringapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val tv = TextView(requireContext())
        tv.text = "Home - Welcome, Parent!\n(Student summary will show here)"
        tv.textSize = 18f
        tv.setPadding(32, 32, 32, 32)
        return tv
    }
}