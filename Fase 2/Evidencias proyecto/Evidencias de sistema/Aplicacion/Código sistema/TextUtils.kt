package com.sara.robot.core

object TextUtils {
    // Elimina caracteres de Markdown para evitar que el TTS lea "asterisco asterisco"
    fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("[*#_`~>\\[\\]()]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // Divide respuestas en oraciones si se requiere verbalización progresiva
    fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
    }
}

