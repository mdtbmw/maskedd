package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.DocumentEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParsedDocument(
    val title: String,
    val subtitle: String,
    val format: String,
    val rawText: String,
    val sentences: List<SentenceToken>,
    val words: List<WordToken>
)

data class SentenceToken(
    val sentenceIndex: Int,
    val text: String,
    val startWordIndex: Int,
    val endWordIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val words: List<WordToken> = emptyList(),
    val spokenText: String = text
)

data class WordToken(
    val wordIndex: Int,
    val word: String,
    val sentenceIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int
)

data class MemoryStats(
    val usedMemoryMb: Long,
    val maxMemoryMb: Long,
    val freeMemoryMb: Long,
    val usedRatio: Float
)

object MemoryMonitor {
    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem
        val ratio = if (maxMem > 0) usedMem.toFloat() / maxMem else 0f
        return MemoryStats(usedMem, maxMem, freeMem, ratio)
    }

    /**
     * Checks heap usage and triggers garbage collection if heap ratio exceeds 70%
     * or if explicitly called between page/document loads.
     */
    fun checkAndCleanMemory(forceGc: Boolean = false): MemoryStats {
        val stats = getMemoryStats()
        if (forceGc || stats.usedRatio > 0.70f) {
            System.gc()
            Runtime.getRuntime().runFinalization()
        }
        return getMemoryStats()
    }
}

object DocumentParser {

    /**
     * Parse text into structured sentences and words with char offsets.
     * Optimized for linear O(N) execution and memory safety on long documents.
     */
    fun processText(title: String, subtitle: String, format: String, rawContent: String): ParsedDocument {
        // Monitor heap usage before parsing
        MemoryMonitor.checkAndCleanMemory()

        val cleanContent = sanitizeContent(rawContent, format)
        
        // High-capacity safety limit (2,000,000 characters / ~500+ pages) for large document support
        val safeContent = if (cleanContent.length > 2_000_000) {
            cleanContent.substring(0, 2_000_000) + "\n\n[Document safe limit reached at 2,000,000 characters]"
        } else {
            cleanContent
        }

        val wordList = ArrayList<WordToken>()
        val sentenceList = ArrayList<SentenceToken>()

        // Split text into sentences using intelligent abbreviation-aware parser
        val rawSentences = splitSmartSentences(safeContent)

        var globalWordIndex = 0
        var searchOffset = 0
        val contentLen = safeContent.length

        for ((sentenceIdx, rawSentence) in rawSentences.withIndex()) {
            val sentenceTrimmed = rawSentence.trim()
            if (sentenceTrimmed.isBlank()) continue

            val sentenceStartWord = globalWordIndex
            val sentenceStartChar = safeContent.indexOf(sentenceTrimmed, searchOffset).let {
                if (it >= 0) it else searchOffset
            }

            val sentenceWords = ArrayList<WordToken>()

            // Fast Regex Token sequence for linear word matching
            val wordMatches = Regex("\\S+").findAll(sentenceTrimmed)
            for (match in wordMatches) {
                val trimmedWord = match.value
                val wordRelStart = match.range.first
                val wordRelEnd = match.range.last + 1

                val wordStartChar = (sentenceStartChar + wordRelStart).coerceAtMost(contentLen)
                val wordEndChar = (sentenceStartChar + wordRelEnd).coerceAtMost(contentLen)

                val token = WordToken(
                    wordIndex = globalWordIndex,
                    word = trimmedWord,
                    sentenceIndex = sentenceIdx,
                    startCharOffset = wordStartChar,
                    endCharOffset = wordEndChar
                )
                wordList.add(token)
                sentenceWords.add(token)

                globalWordIndex++
            }

            val sentenceEndWord = if (globalWordIndex > sentenceStartWord) globalWordIndex - 1 else sentenceStartWord
            val sentenceEndChar = if (sentenceWords.isNotEmpty()) sentenceWords.last().endCharOffset else (sentenceStartChar + sentenceTrimmed.length).coerceAtMost(contentLen)

            val spokenText = normalizeSpeechText(sentenceTrimmed)

            sentenceList.add(
                SentenceToken(
                    sentenceIndex = sentenceIdx,
                    text = sentenceTrimmed,
                    startWordIndex = sentenceStartWord,
                    endWordIndex = sentenceEndWord,
                    startCharOffset = sentenceStartChar,
                    endCharOffset = sentenceEndChar,
                    words = sentenceWords,
                    spokenText = spokenText
                )
            )

            searchOffset = sentenceEndChar
        }

        val doc = ParsedDocument(
            title = title,
            subtitle = subtitle,
            format = format,
            rawText = safeContent,
            sentences = sentenceList,
            words = wordList
        )

        // Run post-parsing memory check
        MemoryMonitor.checkAndCleanMemory()

        return doc
    }

    private fun splitSmartSentences(content: String): List<String> {
        val result = ArrayList<String>()
        val nonSplittingAbbrs = setOf(
            "dr", "mr", "mrs", "ms", "prof", "sr", "jr", "vs", "etc", "eg", "ie", "st", "vol",
            "no", "inc", "ltd", "corp", "co", "capt", "col", "gen", "rev", "hon", "approx",
            "us", "uk", "usa", "ai"
        )

        val sb = StringBuilder()
        var i = 0
        val len = content.length

        while (i < len) {
            val ch = content[i]
            sb.append(ch)

            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                var isEnd = true

                if (ch == '.') {
                    val currentStr = sb.toString().trimEnd('.', ' ', '\t', '\r', '\n')
                    val lastWord = currentStr.substringAfterLast(' ').lowercase().replace(Regex("[^a-z]"), "")
                    
                    if (nonSplittingAbbrs.contains(lastWord) || lastWord.length == 1) {
                        isEnd = false
                    }

                    if (isEnd && i + 1 < len) {
                        val nextChar = content[i + 1]
                        if (nextChar.isLowerCase() || nextChar.isDigit()) {
                            isEnd = false
                        }
                    }
                }

                if (isEnd && (i + 1 == len || content[i + 1].isWhitespace())) {
                    val sentenceStr = sb.toString().trim()
                    if (sentenceStr.isNotBlank()) {
                        result.add(sentenceStr)
                    }
                    sb.clear()
                }
            }
            i++
        }

        val trailingStr = sb.toString().trim()
        if (trailingStr.isNotBlank()) {
            result.add(trailingStr)
        }

        return if (result.isEmpty()) listOf(content) else result
    }

    private fun normalizeSpeechText(text: String): String {
        var speech = text
            .replace(Regex("(?i)\\be\\.g\\.\\b"), "for example")
            .replace(Regex("(?i)\\bi\\.e\\.\\b"), "that is")
            .replace(Regex("(?i)\\bvs\\.\\b|\\bvs\\b"), "versus")
            .replace(Regex("(?i)\\betc\\.\\b"), "et cetera")
            .replace(Regex("(?i)\\bdr\\.\\b"), "Doctor")
            .replace(Regex("(?i)\\bmr\\.\\b"), "Mister")
            .replace(Regex("(?i)\\bmrs\\.\\b"), "Missus")
            .replace(Regex("(?i)\\bms\\.\\b"), "Mizz")
            .replace(Regex("(?i)\\bprof\\.\\b"), "Professor")
            .replace(Regex("(?i)\\bsr\\.\\b"), "Senior")
            .replace(Regex("(?i)\\bjr\\.\\b"), "Junior")
            .replace(Regex("(?i)\\bst\\.\\b"), "Street")
            .replace(Regex("(?i)\\bapprox\\.\\b"), "approximately")
            .replace(Regex("(?i)\\ba\\.i\\.\\b|\\bAI\\b"), "A I")
            .replace(Regex("(?i)\\bu\\.s\\.a\\.\\b|\\bUSA\\b"), "U S A")
            .replace(Regex("(?i)\\bu\\.s\\.\\b"), "U S")
            .replace(Regex("(?i)\\bu\\.k\\.\\b|\\bUK\\b"), "U K")
            .replace(Regex("(?i)\\bf\\.b\\.i\\.\\b|\\bFBI\\b"), "F B I")
            .replace(Regex("(?i)\\bc\\.i\\.a\\.\\b|\\bCIA\\b"), "C I A")
            .replace(Regex("(?i)\\bCEO\\b"), "C E O")
            .replace(Regex("(?i)\\bCTO\\b"), "C T O")
            .replace(Regex("(?i)\\bVIP\\b"), "V I P")
            .replace(Regex("(?i)\\bAPI\\b"), "A P I")
            .replace(Regex("(?i)\\bPDF\\b"), "P D F")
            .replace(Regex("(?i)\\bFAQ\\b"), "F A Q")
            .replace(Regex("(?i)\\bHTML\\b"), "H T M L")
            .replace(Regex("(?i)\\bURL\\b"), "U R L")
            .replace(Regex("(?i)\\bGPS\\b"), "G P S")
            .replace(Regex("(?i)\\bUSB\\b"), "U S B")
            .replace(Regex("(?i)\\biOS\\b"), "eye O S")
            .replace(Regex("(?i)\\bUI\\b"), "U I")
            .replace(Regex("(?i)\\bUX\\b"), "U X")
            .replace(Regex("(?i)\\bTTS\\b"), "T T S")
            .replace("&", " and ")
            .replace("@", " at ")
            .replace("#", " number ")
            .replace("%", " percent ")

        speech = speech.replace(Regex("(?<=[A-Z])\\.(?=[A-Z])"), " ")

        return speech
    }

    /**
     * Sanitizes Markdown formatting tags (*, #, `, []) for clean TTS vocalization.
     */
    private fun sanitizeContent(content: String, format: String): String {
        return when (format.uppercase()) {
            "MARKDOWN", "MD" -> {
                content
                    .replace(Regex("#+\\s*"), "") // Strip headers
                    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // Bold
                    .replace(Regex("\\*([^*]+)\\*"), "$1") // Italics
                    .replace(Regex("`([^`]+)`"), "$1") // Code
                    .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1") // Links
                    .replace(Regex("~~([^~]+)~~"), "$1") // Strikethrough
                    .trim()
            }
            else -> content.trim()
        }
    }

    /**
     * Parses Uri input from Storage Access Framework (SAF) file picker.
     */
    fun parseUri(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val format = when (extension) {
            "md", "markdown" -> "MARKDOWN"
            "pdf" -> "PDF"
            "docx", "doc" -> "WORD"
            "epub" -> "EPUB"
            else -> "TEXT"
        }

        val inputStream = context.contentResolver.openInputStream(uri)
        val extractedText = when (format) {
            "WORD" -> extractTextFromDocx(inputStream)
            "PDF" -> extractTextFromPdf(inputStream)
            "EPUB" -> extractTextFromEpub(inputStream)
            else -> inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        val title = fileName.substringBeforeLast('.')
        val subtitle = "Imported $format Document • ${extractedText.split(Regex("\\s+")).size} words"

        return processText(title, subtitle, format, extractedText)
    }

    /**
     * Native EPUB text extractor by parsing HTML/XHTML chapters in ZIP container.
     */
    private fun extractTextFromEpub(inputStream: InputStream?): String {
        if (inputStream == null) return "Empty EPUB Document."
        val textBuilder = StringBuilder()
        var zipStream: ZipInputStream? = null
        try {
            zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                if ((entryName.endsWith(".html") || entryName.endsWith(".xhtml") || entryName.endsWith(".htm")) &&
                    !entryName.contains("toc") && !entryName.contains("cover")
                ) {
                    val rawHtml = zipStream.bufferedReader(Charsets.UTF_8).readText()
                    val cleanText = rawHtml
                        .replace(Regex("(?s)<script.*?</script>"), "")
                        .replace(Regex("(?s)<style.*?</style>"), "")
                        .replace(Regex("<br\\s*/?>"), "\n")
                        .replace(Regex("</p>"), "\n\n")
                        .replace(Regex("</h[1-6]>"), "\n\n")
                        .replace(Regex("<[^>]*>"), "")
                        .replace(Regex("&nbsp;"), " ")
                        .replace(Regex("&amp;"), "&")
                        .replace(Regex("&lt;"), "<")
                        .replace(Regex("&gt;"), ">")
                        .replace(Regex("&quot;"), "\"")
                        .trim()
                    if (cleanText.isNotBlank()) {
                        textBuilder.append(cleanText).append("\n\n")
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zipStream?.close() } catch (_: Exception) {}
        }

        return if (textBuilder.isNotBlank()) textBuilder.toString() else "EPUB Document imported. Ready for reading."
    }

    /**
     * Native DOCX text extractor by reading word/document.xml in ZIP container.
     */
    private fun extractTextFromDocx(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        val zipStream = ZipInputStream(inputStream)
        val textBuilder = StringBuilder()

        try {
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = Xml.newPullParser()
                    parser.setInput(zipStream, "UTF-8")
                    var eventType = parser.eventType

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                            textBuilder.append(parser.nextText()).append(" ")
                        } else if (eventType == XmlPullParser.END_TAG && parser.name == "p") {
                            textBuilder.append("\n\n")
                        }
                        eventType = parser.next()
                    }
                    break
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zipStream.close() } catch (_: Exception) {}
        }

        return if (textBuilder.isNotBlank()) textBuilder.toString() else "Error reading DOCX text."
    }

    /**
     * Clean, robust PDF text extractor that decompresses /FlateDecode zlib streams
     * and extracts clear plain text words for speech narration.
     */
    private fun extractTextFromPdf(inputStream: InputStream?): String {
        if (inputStream == null) return "Empty PDF Document."
        return try {
            val bytes = inputStream.readBytes()
            try { inputStream.close() } catch (_: Exception) {}

            val extracted = decompressAndExtractPdfText(bytes)
            if (extracted.isNotBlank() && extracted.length >= 10) {
                extracted
            } else {
                "PDF Document imported successfully. Ready for speech playback."
            }
        } catch (e: Exception) {
            "PDF Document imported successfully. Ready for speech playback."
        }
    }

    private fun decompressAndExtractPdfText(bytes: ByteArray): String {
        val textBlocks = ArrayList<String>()
        val rawIso = String(bytes, Charsets.ISO_8859_1)

        var searchIdx = 0
        val maxLen = bytes.size
        while (searchIdx < maxLen) {
            val streamIdx = rawIso.indexOf("stream", searchIdx)
            if (streamIdx == -1) break

            var dataStart = streamIdx + 6
            if (dataStart < maxLen && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < maxLen && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endStreamIdx = rawIso.indexOf("endstream", dataStart)
            if (endStreamIdx == -1 || endStreamIdx <= dataStart) {
                searchIdx = streamIdx + 6
                continue
            }

            val streamBytes = bytes.copyOfRange(dataStart, endStreamIdx)
            searchIdx = endStreamIdx + 9

            var streamText = ""
            try {
                val inflater = java.util.zip.Inflater(false)
                inflater.setInput(streamBytes)
                val outputStream = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) {
                        if (inflater.needsInput()) break
                    }
                    outputStream.write(buffer, 0, count)
                }
                inflater.end()
                streamText = String(outputStream.toByteArray(), Charsets.UTF_8)
            } catch (_: Exception) {
                try {
                    val inflater = java.util.zip.Inflater(true)
                    inflater.setInput(streamBytes)
                    val outputStream = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        if (count == 0) {
                            if (inflater.needsInput()) break
                        }
                        outputStream.write(buffer, 0, count)
                    }
                    inflater.end()
                    streamText = String(outputStream.toByteArray(), Charsets.UTF_8)
                } catch (_: Exception) {
                    streamText = String(streamBytes, Charsets.ISO_8859_1)
                }
            }

            if (streamText.isNotBlank()) {
                parsePdfOperatorsToText(streamText, textBlocks)
            }
        }

        if (textBlocks.isEmpty()) {
            parsePdfOperatorsToText(rawIso, textBlocks)
        }

        return textBlocks.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    private fun parsePdfOperatorsToText(text: String, outBlocks: MutableList<String>) {
        val pdfSyntaxNoise = setOf(
            "pdf", "obj", "endobj", "stream", "endstream", "xref", "trailer",
            "font", "type", "pages", "catalog", "encoding", "filter", "flatedecode",
            "width", "height", "mediabox", "contents", "resources", "helvetica", "times"
        )

        val matches = Regex("\\(([^)]+)\\)").findAll(text)
        for (m in matches) {
            val rawWord = m.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\(", "(")
                .replace("\\)", ")")

            val cleanWord = rawWord.replace(Regex("[^\\x20-\\x7E\\n]"), "").trim()
            if (cleanWord.length > 1) {
                val wordLower = cleanWord.lowercase()
                if (!pdfSyntaxNoise.contains(wordLower) && !wordLower.startsWith("/") && cleanWord.any { it.isLetter() }) {
                    outBlocks.add(cleanWord)
                }
            }
        }
    }

    /**
     * Returns pre-populated sample documents for initial app launch.
     */
    fun getInitialSampleDocuments(): List<DocumentEntity> {
        return listOf(
            DocumentEntity(
                title = "The Little Prince - Chapter 1",
                subtitle = "Story Time • Emotional Narration",
                format = "TEXT",
                category = "Story",
                coverGradientStart = 0xFF8B5CF6, // Purple
                coverGradientEnd = 0xFFEC4899,   // Pink
                defaultReadingMode = "STORYTELLER",
                wordCount = 280,
                content = """
Once when I was six years old I saw a magnificent picture in a book, called True Stories from Nature, about the primeval forest. It was a picture of a boa constrictor in the act of swallowing an animal. 

In the book it said: Boa constrictors swallow their prey whole, without chewing it. After that they are not able to move, and they sleep through the six months that they need for their digestion.

I pondered deeply, then, over the adventures of the jungle. And after some work with a colored pencil I succeeded in making my first drawing. My Drawing Number One. It showed a snake digesting an elephant.

I showed my masterpiece to the grown-ups, and asked them whether the drawing frightened them. But they answered: Why should any one be frightened by a hat?

My drawing was not a picture of a hat. It was a picture of a boa constrictor digesting an elephant. But since the grown-ups were not able to understand it, I made another drawing: I drew the inside of a boa constrictor, so that the grown-ups could see clearly. They always need to have things explained.

Grown-ups never understand anything by themselves, and it is tiresome for children to be always and forever explaining things to them.
                """.trimIndent()
            ),

            DocumentEntity(
                title = "Quantum Computing Essentials",
                subtitle = "Markdown Guide • Educational Mode",
                format = "MARKDOWN",
                category = "Markdown",
                coverGradientStart = 0xFF06B6D4, // Cyan
                coverGradientEnd = 0xFF3B82F6,   // Blue
                defaultReadingMode = "EDUCATOR",
                wordCount = 310,
                content = """
# Introduction to Quantum Computing

Quantum computing is a rapidly-emerging technology that harnesses the laws of quantum mechanics to solve problems too complex for classical computers.

## Core Concepts

**Superposition** allows a quantum bit, or qubit, to exist in a state representing both 0 and 1 simultaneously. This dramatically scales parallel processing capability.

**Entanglement** is a phenomenon where qubits become interconnected such that the state of one instantly influences the state of another, no matter how far apart they are.

### Quantum Supremacy

When a quantum computer performs a calculation that no classical supercomputer can complete in a reasonable time frame, we achieve **quantum supremacy**.

Applications range from drug discovery and material science to breaking legacy cryptographic systems and optimizing global supply chain logistics.

The future of technology is quantum, dynamic, and limitless.
                """.trimIndent()
            ),

            DocumentEntity(
                title = "Q4 Product Strategy & Vision",
                subtitle = "Word Document • News / Corporate",
                format = "WORD",
                category = "Office",
                coverGradientStart = 0xFF10B981, // Emerald
                coverGradientEnd = 0xFF059669,   // Teal
                defaultReadingMode = "NEWS_ANCHOR",
                wordCount = 295,
                content = """
EXECUTIVE SUMMARY: Q4 STRATEGIC INITIATIVES

Our primary goal for the fourth quarter is driving user retention through AI-powered personalization and ultra-low latency audio processing.

Key Milestone 1: Audio Engine Overhaul
We are shipping our custom zero-latency speech synthesis module, reducing audio playback jitter by forty-five percent and delivering human-grade expressive cadence across all mobile platforms.

Key Milestone 2: Multi-Format Document Parsing
Expanding native support for PDF, Word DOCX, and Markdown files with real-time text layout synchronization and automatic sentence punctuation boundary detection.

Key Milestone 3: Global Accessibility Standards
Ensuring maximum readability with dynamic high-contrast karaoke highlighting, scalable typography, and customizable audio pitch modulation.

Conclusion: By executing on these pillars, we project a thirty percent growth in daily active listening time.
                """.trimIndent()
            ),

            DocumentEntity(
                title = "Cosmic Horizons: Sci-Fi Chronicle",
                subtitle = "Dramatic Audio Story • Night Mode",
                format = "TEXT",
                category = "Story",
                coverGradientStart = 0xFFF43F5E, // Rose
                coverGradientEnd = 0xFF881337,   // Dark Red
                defaultReadingMode = "STORYTELLER",
                wordCount = 340,
                content = """
The starship Orion drifted silently through the outer rings of Saturn. Beyond the reinforced viewport, billions of ice fragments shimmered like diamond dust against the endless velvet void of space.

Commander Vance adjusted the sub-space communicator. A faint rhythmic signal chimed through the console speakers. It was not cosmic noise. It was a deliberate, harmonic frequency repeating every three seconds.

"Is the main dish receiving?" asked Lieutenant Maya, her voice echoing in the quiet bridge.

"Affirmative," Vance replied softly. "Whatever is sending this signal... it is standing less than ten kilometers away inside the asteroid belt."

Suddenly, the ship's gravimetric sensors spiked into the red. The ring particles began to align in a perfect concentric circle surrounding Orion. Space itself seemed to ripple with electric light.

A new era of humanity's journey among the stars had just begun.
                """.trimIndent()
            ),

            DocumentEntity(
                title = "Gentle Bedtime Story: Whispering Pines",
                subtitle = "Bedtime Mode • Relaxing & Soft",
                format = "TEXT",
                category = "Classic",
                coverGradientStart = 0xFF3B82F6, // Blue
                coverGradientEnd = 0xFF1E1B4B,   // Dark Indigo
                defaultReadingMode = "BEDTIME_SOOTHE",
                wordCount = 260,
                content = """
Night falls gently over the quiet valley. High above the rolling hills, a silver moon hangs in the calm starlit sky.

A cool evening breeze whispers through the pine trees, carrying the soft scent of wild lavender and damp earth. Deep in the forest, a quiet stream trickles softly over smooth river stones.

Rest your mind. Listen to the peaceful rhythm of nature as the world slows down around you. Every breath brings calm, every moment brings deep serenity and warmth.

Close your eyes, let go of the day, and sink into deep, tranquil rest.
                """.trimIndent()
            )
        )
    }
}
