package com.example.tts

import java.util.Locale
import java.util.regex.Pattern

data class PipelineResult(
    val originalText: String,
    val homographDisambiguatedText: String,
    val normalizedText: String,
    val coarticulatedText: String,
    val ssmlOutput: String
)

/**
 * MaskedD Advanced Text Normalization & Contextual Prosody Pipeline
 * Eliminates robotic TTS sounds through Homograph Disambiguation, Contextual Number/Acronym Normalization,
 * Phonotactic Coarticulation/Slur Logic, and SSML Micro-Pause Injection.
 */
object TextNormalizationPipeline {

    // --- 1. Homograph Disambiguator ---
    private data class HomographRule(
        val word: String,
        val phoneticVariantA: String, // e.g. "red" or "led"
        val phoneticVariantB: String, // e.g. "reed" or "leed"
        val triggersForA: List<String>,
        val triggersForB: List<String>
    )

    private val HOMOGRAPH_RULES = listOf(
        HomographRule(
            word = "read",
            phoneticVariantA = "red",
            phoneticVariantB = "reed",
            triggersForA = listOf("yesterday", "last", "already", "had", "was", "been", "earlier", "ago", "previously", "have"),
            triggersForB = listOf("will", "can", "should", "must", "to", "always", "now", "today", "please", "going", "wanna")
        ),
        HomographRule(
            word = "lead",
            phoneticVariantA = "led",
            phoneticVariantB = "leed",
            triggersForA = listOf("heavy", "metal", "pipe", "poisoning", "bullet", "pencil", "dense", "toxic"),
            triggersForB = listOf("to", "will", "can", "way", "team", "us", "them", "forward", "guide", "role")
        ),
        HomographRule(
            word = "wind",
            phoneticVariantA = "wind",
            phoneticVariantB = "waind",
            triggersForA = listOf("strong", "cold", "blowing", "gust", "storm", "chilly", "breeze", "air"),
            triggersForB = listOf("up", "clock", "road", "path", "tightly", "down", "spool")
        ),
        HomographRule(
            word = "live",
            phoneticVariantA = "laiv",
            phoneticVariantB = "liv",
            triggersForA = listOf("broadcast", "stream", "show", "performance", "concert", "tv", "on", "coverage"),
            triggersForB = listOf("in", "at", "here", "there", "peacefully", "forever", "to", "with")
        ),
        HomographRule(
            word = "tear",
            phoneticVariantA = "teer",
            phoneticVariantB = "tair",
            triggersForA = listOf("eye", "cried", "fell", "salty", "down", "face", "drop", "sad"),
            triggersForB = listOf("apart", "down", "up", "paper", "cloth", "sheet", "rip")
        ),
        HomographRule(
            word = "bow",
            phoneticVariantA = "boh",
            phoneticVariantB = "bau",
            triggersForA = listOf("tie", "arrow", "ribbon", "hair", "knot", "string"),
            triggersForB = listOf("down", "before", "respect", "head", "curtsy", "audience")
        )
    )

    fun disambiguateHomographs(text: String): String {
        val tokens = text.split(Regex("\\s+"))
        if (tokens.isEmpty()) return text

        val resultTokens = mutableListOf<String>()

        for (i in tokens.indices) {
            val token = tokens[i]
            val cleanWord = token.lowercase().replace(Regex("[^a-z]"), "")
            val rule = HOMOGRAPH_RULES.find { it.word == cleanWord }

            if (rule != null) {
                // Inspect context window of +/- 4 words
                val startWindow = (i - 4).coerceAtLeast(0)
                val endWindow = (i + 5).coerceAtMost(tokens.size)
                val contextString = tokens.subList(startWindow, endWindow)
                    .joinToString(" ") { it.lowercase().replace(Regex("[^a-z]"), "") }

                val substitute = when {
                    rule.triggersForA.any { contextString.contains(it) } -> rule.phoneticVariantA
                    rule.triggersForB.any { contextString.contains(it) } -> rule.phoneticVariantB
                    cleanWord == "read" -> "red" // default to past tense for literature
                    else -> rule.phoneticVariantB
                }

                // Preserve punctuation prefix / suffix
                val prefix = token.takeWhile { !it.isLetterOrDigit() }
                val suffix = token.takeLastWhile { !it.isLetterOrDigit() }
                resultTokens.add("$prefix$substitute$suffix")
            } else {
                resultTokens.add(token)
            }
        }

        return resultTokens.joinToString(" ")
    }

    // --- 2. Acronym & Number Normalizer ---
    private val NUMBER_WORDS = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val TENS_WORDS = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun numberToWords(n: Long): String {
        if (n < 0) return "minus " + numberToWords(-n)
        if (n < 20) return NUMBER_WORDS[n.toInt()]
        if (n < 100) {
            val ten = (n / 10).toInt()
            val rem = (n % 10).toInt()
            return TENS_WORDS[ten] + if (rem > 0) "-" + NUMBER_WORDS[rem] else ""
        }
        if (n < 1000) {
            val hundred = (n / 100).toInt()
            val rem = n % 100
            return NUMBER_WORDS[hundred] + " hundred" + if (rem > 0) " " + numberToWords(rem) else ""
        }
        if (n < 1000000) {
            val thousand = n / 1000
            val rem = n % 1000
            return numberToWords(thousand) + " thousand" + if (rem > 0) " " + numberToWords(rem) else ""
        }
        return n.toString()
    }

    fun yearToWords(year: Int): String {
        return when {
            year in 1000..1999 -> {
                val c1 = year / 100
                val c2 = year % 100
                val str1 = numberToWords(c1.toLong())
                when {
                    c2 == 0 -> "$str1 hundred"
                    c2 < 10 -> "$str1 oh-${numberToWords(c2.toLong())}"
                    else -> "$str1 ${numberToWords(c2.toLong())}"
                }
            }
            year in 2000..2099 -> {
                when {
                    year == 2000 -> "two thousand"
                    year < 2010 -> "two thousand " + numberToWords((year % 100).toLong())
                    else -> "twenty " + numberToWords((year % 100).toLong())
                }
            }
            else -> numberToWords(year.toLong())
        }
    }

    fun normalizeAcronymsAndNumbers(text: String): String {
        var result = text

        // 1. Currency $50 or $12.50
        val currencyPattern = Pattern.compile("\\$(\\d+)(\\.\\d{2})?")
        val currencyMatcher = currencyPattern.matcher(result)
        val sbCurrency = StringBuffer()
        while (currencyMatcher.find()) {
            val dollars = currencyMatcher.group(1)?.toLongOrNull() ?: 0L
            val centsPart = currencyMatcher.group(2)
            val dollarStr = numberToWords(dollars) + if (dollars == 1L) " dollar" else " dollars"
            val centsStr = if (centsPart != null) {
                val cents = centsPart.removePrefix(".").toLongOrNull() ?: 0L
                if (cents > 0) " and " + numberToWords(cents) + " cents" else ""
            } else ""
            currencyMatcher.appendReplacement(sbCurrency, "$dollarStr$centsStr")
        }
        currencyMatcher.appendTail(sbCurrency)
        result = sbCurrency.toString()

        // 2. Percentages 85%
        result = result.replace(Regex("(\\d+)%")) { match ->
            val num = match.groupValues[1].toLongOrNull() ?: 0L
            "${numberToWords(num)} percent"
        }

        // 3. Time 8:30 or 10:15
        result = result.replace(Regex("\\b(\\d{1,2}):(\\d{2})\\b")) { match ->
            val hour = match.groupValues[1].toLongOrNull() ?: 0L
            val min = match.groupValues[2].toLongOrNull() ?: 0L
            val hourStr = numberToWords(hour)
            val minStr = if (min == 0L) "o'clock" else if (min < 10) "oh-${numberToWords(min)}" else numberToWords(min)
            "$hourStr $minStr"
        }

        // 4. Dates vs Math years (1984, 2024, etc.)
        val yearPattern = Pattern.compile("\\b(1[89]\\d\\d|20\\d\\d)\\b")
        val yearMatcher = yearPattern.matcher(result)
        val sbYear = StringBuffer()
        while (yearMatcher.find()) {
            val yearVal = yearMatcher.group(1).toInt()
            val startPos = yearMatcher.start()
            val preceding = result.substring((startPos - 15).coerceAtLeast(0), startPos).lowercase()
            val isYearContext = listOf("in", "since", "year", "during", "from", "by", "around", "c.").any { preceding.contains(it) }

            val replacement = if (isYearContext) yearToWords(yearVal) else numberToWords(yearVal.toLong())
            yearMatcher.appendReplacement(sbYear, replacement)
        }
        yearMatcher.appendTail(sbYear)
        result = sbYear.toString()

        // 5. Standalone numbers
        result = result.replace(Regex("\\b(\\d{1,4})\\b")) { match ->
            val num = match.groupValues[1].toLongOrNull() ?: 0L
            numberToWords(num)
        }

        // 6. Contextual Acronyms (WHO org vs pronoun who)
        result = result.replace(Regex("\\bWHO\\b")) {
            if (result.contains("Health") || result.contains("Organization") || result.contains("reported") || result.contains("the WHO")) {
                "W H O"
            } else {
                "who"
            }
        }
        result = result.replace(Regex("\\bNASA\\b"), "NA-SA")
            .replace(Regex("\\bNATO\\b"), "NA-TO")
            .replace(Regex("\\bFBI\\b"), "F B I")
            .replace(Regex("\\bCIA\\b"), "C I A")
            .replace(Regex("\\bUSA\\b"), "U S A")
            .replace(Regex("\\bAPI\\b"), "A P I")
            .replace(Regex("\\bHTML\\b"), "H T M L")
            .replace(Regex("\\bURL\\b"), "U R L")
            .replace(Regex("\\bCOVID\\b"), "CO-VID")

        return result
    }

    // --- 3. Coarticulation & Phonotactic Softening Engine ---
    private val COARTICULATION_REPLACEMENTS = listOf(
        Regex("(?i)\\bwant to\\b") to "wanna",
        Regex("(?i)\\bgoing to\\b") to "gonna",
        Regex("(?i)\\bgot to\\b") to "gotta",
        Regex("(?i)\\bdid you\\b") to "didja",
        Regex("(?i)\\bgive me\\b") to "gimme",
        Regex("(?i)\\blet me\\b") to "lemme",
        Regex("(?i)\\bdon't know\\b") to "dunno",
        Regex("(?i)\\bshould have\\b") to "shoulda",
        Regex("(?i)\\bcould have\\b") to "coulda",
        Regex("(?i)\\bwould have\\b") to "woulda",
        Regex("(?i)\\bkind of\\b") to "kinda",
        Regex("(?i)\\bsort of\\b") to "sorta",
        // Flap-T softening
        Regex("(?i)\\bbutter\\b") to "budder",
        Regex("(?i)\\bwater\\b") to "wadder",
        Regex("(?i)\\bbetter\\b") to "bedder",
        Regex("(?i)\\blittle\\b") to "liddle",
        Regex("(?i)\\bletter\\b") to "ledder"
    )

    fun applyCoarticulation(text: String): String {
        var result = text
        for ((pattern, replacement) in COARTICULATION_REPLACEMENTS) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    // --- 4. SSML & Micro-Pause Generator ---
    fun generateSsml(text: String, rate: Float = 1.0f, pitch: Float = 1.0f): String {
        var processed = text
            .replace(",", ", <break time=\"150ms\"/>")
            .replace(";", "; <break time=\"200ms\"/>")
            .replace("—", "— <break time=\"220ms\"/>")
            .replace(".", ". <break time=\"350ms\"/>")
            .replace("?", "? <break time=\"400ms\"/>")
            .replace("!", "! <break time=\"380ms\"/>")

        val ratePercent = "${(rate * 100).toInt()}%"
        val pitchSemitones = "${((pitch - 1.0f) * 6).toInt()}st"

        return """<speak>
  <prosody rate="$ratePercent" pitch="$pitchSemitones">
    $processed
  </prosody>
</speak>""".trimIndent()
    }

    /**
     * Executes the full end-to-end normalization pipeline.
     */
    fun processFullPipeline(inputText: String, rate: Float = 1.0f, pitch: Float = 1.0f): PipelineResult {
        val step1Homographs = disambiguateHomographs(inputText)
        val step2Normalized = normalizeAcronymsAndNumbers(step1Homographs)
        val step3Coarticulated = applyCoarticulation(step2Normalized)
        val ssmlOutput = generateSsml(step3Coarticulated, rate, pitch)

        return PipelineResult(
            originalText = inputText,
            homographDisambiguatedText = step1Homographs,
            normalizedText = step2Normalized,
            coarticulatedText = step3Coarticulated,
            ssmlOutput = ssmlOutput
        )
    }
}
