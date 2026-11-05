package com.sara.robot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilsTest {

    @Test
    fun stripMarkdown_removesAsterisksAndSpecialCharacters() {
        val input = "Hola **amigo**, este es un *ejemplo* con `código` y #título."
        val result = TextUtils.stripMarkdown(input)
        assertEquals("Hola amigo, este es un ejemplo con código y título.", result)
    }

    @Test
    fun splitSentences_dividesTextByPunctuation() {
        val input = "Hola Sara. ¿Cómo estás hoy? ¡Espero que muy bien!"
        val sentences = TextUtils.splitSentences(input)
        assertEquals(3, sentences.size)
        assertEquals("Hola Sara.", sentences[0])
        assertEquals("¿Cómo estás hoy?", sentences[1])
        assertEquals("¡Espero que muy bien!", sentences[2])
    }

    @Test
    fun saraPrompt_containsRequiredRules() {
        assertTrue(SaraPrompt.SYSTEM_PROMPT.contains("Sara"))
        assertTrue(SaraPrompt.SYSTEM_PROMPT.contains("médico especialista"))
        assertTrue(SaraPrompt.SALUDO_INICIAL.isNotBlank())
    }
}

