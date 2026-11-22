package com.tuna.finlife.assistant

import kotlin.math.roundToLong

/**
 * Checks a model reply before it is shown: every number must come from the engine facts, and the reply
 * must not give advice. A reply that fails is asked again once, then replaced by an app message.
 */
internal object ReplyGuard {
    // Grouped with spaces/apostrophes ("1 200", "1'200,50"), grouped with commas ("1,200.50"), or plain ("1200.5").
    private val number = Regex(
        """\d{1,3}(?:[   '’]\d{3})+(?:[.,]\d+)?|\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:[.,]\d+)?""",
    )

    /** Each number in [text] with its value in hundredths. Signs are ignored: "−300" and "300" are one figure. */
    fun numbers(text: String): List<Pair<String, Long>> =
        number.findAll(text).map { it.value to hundredths(it.value) }.toList()

    private fun hundredths(token: String): Long {
        if (token.count { it.isDigit() } > 15) return Long.MAX_VALUE
        val last = token.indexOfLast { !it.isDigit() }
        if (last < 0) return token.toLong() * 100
        val separator = token[last]
        // "1,200" is read as twelve hundred (English grouping); "1,5" and "1.25" are decimals.
        val commaGrouped = separator == ',' && token.all { it.isDigit() || it == ',' } &&
            token.split(',').drop(1).all { it.length == 3 }
        if ((separator != '.' && separator != ',') || commaGrouped) return token.filter { it.isDigit() }.toLong() * 100
        val whole = token.substring(0, last).filter { it.isDigit() }.ifEmpty { "0" }.toLong()
        val fraction = token.substring(last + 1)
        return whole * 100 + ("0.$fraction".toDouble() * 100).roundToLong()
    }

    /** Numbers of [reply] that are neither in [facts] nor a fact amount rounded to whole units. Zero is always fine. */
    fun unsupportedNumbers(reply: String, facts: String): List<String> {
        val known = numbers(facts).map { it.second }
        val allowed = known.toSet() + known.map { (it / 100.0).roundToLong() * 100 } + 0L
        return numbers(reply).filter { it.second !in allowed }.map { it.first }.distinct()
    }

    // Conservative metric binding, in both UI languages. Unknown monetary wording is retried.
    // This is a lexical guard, not a proof of arbitrary natural-language claims or time attribution.
    private val metricAliases = linkedMapOf(
        "netWorth" to listOf("net worth", "patrimoine net"),
        "balance" to listOf("theoretical balance", "balance", "solde théorique", "solde"),
        "unpaid" to listOf("unpaid bills", "unpaid obligations", "factures impayées", "impayés"),
        "cash" to listOf("cash on the account", "available bank balance", "cash", "solde bancaire disponible"),
        "salary" to listOf("salary", "salaire"),
        "income" to listOf("income", "received", "revenus", "revenu", "reçu"),
        "expenses" to listOf("expenses", "dépenses"),
        "rent" to listOf("rent", "loyer"),
        "insurance" to listOf("health insurance", "assurance maladie"),
        "groceries" to listOf("groceries", "courses"),
        "tuition" to listOf("tuition", "frais de scolarité"),
        "cost" to listOf("cost", "coût"),
    )
    private val metric = Regex(metricAliases.values.flatten().sortedByDescending { it.length }
        .joinToString("|") { "\\b" + Regex.escape(it) + "\\b" }, RegexOption.IGNORE_CASE)
    private val amount = Regex("([−+\\-]?)(" + number.pattern + ")\\s*(?:CHF|EUR|USD|GBP)\\b", RegexOption.IGNORE_CASE)

    private fun metricAmounts(text: String): List<Pair<String?, Long>> = buildList {
        // Do not split decimal amounts; reset labels at sentence boundaries and newlines.
        for (sentence in text.replace("(cash minus unpaid bills)", "").split(Regex("[;!?!\\n]|\\.(?=\\s|$)"))) {
            for (match in amount.findAll(sentence)) {
                val label = metric.findAll(sentence.substring(0, match.range.first)).lastOrNull()?.value?.lowercase()
                val key = metricAliases.entries.firstOrNull { label in it.value }?.key
                val sign = if (match.groupValues[1] in listOf("-", "−")) -1 else 1
                add(key to sign * hundredths(match.groupValues[2]))
            }
        }
    }

    /** A real number attached to the wrong metric or with the wrong sign must not pass. */
    fun unsupportedMetricAmounts(reply: String, facts: String): List<String> {
        val known = metricAmounts(facts).filter { it.first != null }.toSet()
        val rounded = known.map { (key, value) -> key to (value / 100.0).roundToLong() * 100 }.toSet()
        return metricAmounts(reply).filter { it.first == null || (it !in known && it !in rounded) }
            .map { "${it.first ?: "unlabelled amount"}: ${it.second / 100.0}" }.distinct()
    }

    private val advice = listOf(
        "i recommend", "i would recommend", "i suggest", "i advise", "you should", "maya should", "she should",
        "my advice", "it is better to", "it's better to", "the best option", "the best choice",
        "je recommande", "je conseille", "je vous conseille", "je te conseille", "je suggère", "vous devriez",
        "tu devrais", "maya devrait", "elle devrait", "mon conseil", "il vaut mieux", "la meilleure option",
        "le meilleur choix",
    )

    /** Advice phrases found in [reply] (case-insensitive). */
    fun advicePhrases(reply: String): List<String> {
        val lower = reply.lowercase().replace('’', '\'')
        return advice.filter { it in lower }
    }
}
