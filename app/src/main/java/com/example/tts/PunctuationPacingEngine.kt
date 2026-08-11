package com.example.tts

data class PacingClause(
    val clauseText: String,
    val pauseAfterMs: Long,
    val intonationStyle: IntonationStyle
)

enum class IntonationStyle {
    NEUTRAL,
    QUESTION_RISING,
    EXCLAMATION_EMPHATIC,
    COMMA_CONTINUATION,
    EM_DASH_SUSPENSE,
    PARAGRAPH_RESOLVING
}

/**
 * Punctuation-Aware Pacing Engine
 * Analyzes sentence structure, syntax boundaries, and clause depth
 * to dynamically auto-adjust silence durations between clauses,
 * preventing robotic sounding run-on sentences.
 */
object PunctuationPacingEngine {

    fun analyzePacing(text: String, speedRate: Float = 1.0f): List<PacingClause> {
        if (text.isBlank()) return emptyList()

        val clauses = mutableListOf<PacingClause>()

        // Split text by punctuation boundaries while keeping delimiters
        val regex = Regex("(?<=[,.!?;:—\n])|(?=[,.!?;:—\n])")
        val rawTokens = text.split(regex).map { it.trim() }.filter { it.isNotEmpty() }

        var currentBuffer = StringBuilder()

        for (token in rawTokens) {
            when (token) {
                "," -> {
                    val phrase = currentBuffer.toString().trim()
                    if (phrase.isNotEmpty()) {
                        val basePause = (140L / speedRate).toLong().coerceIn(80L, 250L)
                        clauses.add(PacingClause(phrase + ",", basePause, IntonationStyle.COMMA_CONTINUATION))
                        currentBuffer.clear()
                    }
                }
                ";", ":" -> {
                    val phrase = currentBuffer.toString().trim()
                    if (phrase.isNotEmpty()) {
                        val basePause = (200L / speedRate).toLong().coerceIn(120L, 350L)
                        clauses.add(PacingClause(phrase + token, basePause, IntonationStyle.NEUTRAL))
                        currentBuffer.clear()
                    }
                }
                "—", "--" -> {
                    val phrase = currentBuffer.toString().trim()
                    if (phrase.isNotEmpty()) {
                        val basePause = (240L / speedRate).toLong().coerceIn(150L, 400L)
                        clauses.add(PacingClause(phrase + " —", basePause, IntonationStyle.EM_DASH_SUSPENSE))
                        currentBuffer.clear()
                    }
                }
                "." -> {
                    val phrase = currentBuffer.toString().trim()
                    if (phrase.isNotEmpty()) {
                        val basePause = (360L / speedRate).toLong().coerceIn(220L, 600L)
                        clauses.add(PacingClause(phrase + ".", basePause, IntonationStyle.PARAGRAPH_RESOLVING))
                        currentBuffer.clear()
                    }
                }
                "?" -> {
                    val phrase = currentBuffer.toString().trim()
                    if (phrase.isNotEmpty()) {
                        val basePause = (400L / speedRate).toLong().coerceIn(250L, 650L)
                        clauses.add(PacingClause(phrase + "?", basePause, IntonationStyle.QUESTION_RISING))
                        currentBuffer.clear()
                    }
                }
                "!" -> {
                    val phrase = currentBuffer.toString().trim()
                    if (phrase.isNotEmpty()) {
                        val basePause = (380L / speedRate).toLong().coerceIn(240L, 600L)
                        clauses.add(PacingClause(phrase + "!", basePause, IntonationStyle.EXCLAMATION_EMPHATIC))
                        currentBuffer.clear()
                    }
                }
                else -> {
                    if (currentBuffer.isNotEmpty()) currentBuffer.append(" ")
                    currentBuffer.append(token)
                }
            }
        }

        if (currentBuffer.isNotEmpty()) {
            val remaining = currentBuffer.toString().trim()
            if (remaining.isNotEmpty()) {
                clauses.add(PacingClause(remaining, (300L / speedRate).toLong(), IntonationStyle.NEUTRAL))
            }
        }

        return clauses
    }
}
