package com.example.mapapp.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow("")
    val logs = _logs.asStateFlow()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        val timestamp = dateFormat.format(Date())
        val newEntry = "[$timestamp] $tag: $message\n"
        _logs.value = newEntry + _logs.value
    }

    fun error(tag: String, message: String, e: Throwable? = null) {
        Log.e(tag, message, e)
        val timestamp = dateFormat.format(Date())
        val newEntry = "[$timestamp] [ERROR] $tag: $message ${e?.message ?: ""}\n"
        _logs.value = newEntry + _logs.value
    }
    
    fun clear() {
        _logs.value = ""
    }
}

