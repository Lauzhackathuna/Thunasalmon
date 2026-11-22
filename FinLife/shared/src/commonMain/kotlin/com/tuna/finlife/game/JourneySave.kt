package com.tuna.finlife.game

import com.tuna.finlife.simulation.*

internal data class RestoredJourney(val data: StartingData, val session: SimulationSession,
    val draft: WhatIfDraft?, val ended: Boolean, val language: String, val theme: String, val playerName: String)

/** Versioned app inputs and recorded calendar, not serialized engine internals.
 * Checksum detects accidental corruption, not malicious tampering. Unknown versions/types fail closed. */
internal object JourneySave {
    fun encode(data: StartingData, session: SimulationSession, draft: WhatIfDraft?, ended: Boolean,
        language: String, theme: String, playerName: String = "Player"): String {
        val validatedName = requireNotNull(PlayerName.normalize(playerName)) { "A valid player name is required" }
        val base = draft?.base ?: session
        val lines = buildList {
            add("FINLIFE|2")
            add("N|${hex(validatedName)}")
            add("R|${base.configuration.randomSeed}|${base.currentMonth}|$ended|$language|$theme")
            StartingField.entries.forEach { add("A|${it.name}|${data[it]}") }
            add("V|${base.currentState.cash.cents}|${base.currentState.netWorth.cents}|${base.currentState.studyMonthsCompleted}|${base.currentState.accountsPayable.cents}")
            draft?.selected?.sortedBy { it.name }?.forEach { add("D|${it.name}") }
            base.selections.forEach { add("C|${hex(it.eventId)}|${hex(it.optionId)}") }
            base.calendar.forEach { e ->
                val head = "E|${hex(e.id)}|${e.month}|"
                add(head + when (e) {
                    is LifeEvent.RentRise -> "rent|${hex(e.rule.contractId)}|${e.rule.annualExtraRate}"
                    is LifeEvent.OneOffExpense -> "expense|${hex(e.label)}|${e.amount.cents}|${e.responsibility.name}"
                    is LifeEvent.Maintenance -> "maintenance|${e.nominalAmount.cents}|${e.responsibility.name}"
                    is LifeEvent.ShortInternshipOffer -> "short|${e.startMonth}|${e.monthlyIncome.cents}"
                    is LifeEvent.CareerBreak -> "break|${e.durationMonths}"
                    is LifeEvent.EpflJobOffer -> "epfl|${e.monthlySalary.cents}|${e.privateCompanyFailureProbability}|${e.riskHorizonMonths}"
                    is LifeEvent.CompanyClosure -> "closure|${hex(e.offerId)}"
                    is LifeEvent.HousingChoice -> {
                        require(e.newRentRule == null) { "Unsupported app housing rule" }
                        "housing|${hex(e.newContract.id)}|${e.newContract.monthlyRent.cents}|${e.newContract.priceMonth}|${e.newContract.type.name}|${e.movingCost.cents}|${e.responsibility.name}"
                    }
                    else -> error("Unsupported app event type: ${e.id}")
                })
            }
        }
        val body = lines.joinToString("\n")
        return body + "\nHASH|" + checksum(body)
    }

    fun decode(text: String): RestoredJourney {
        require(text.length <= 2_000_000)
        val body = text.substringBeforeLast("\nHASH|", "")
        require(body.isNotEmpty() && checksum(body) == text.substringAfterLast("\nHASH|")) { "Save checksum mismatch" }
        val lines = body.lines().map { it.split('|') }
        val version = lines.first().takeIf { it.size == 2 && it[0] == "FINLIFE" }?.get(1)
        require(version in setOf("1", "2")) { "Unsupported save version" }
        require(lines.drop(1).all { it.first() in setOf("R", "A", "V", "D", "C", "E", "N") })
        val playerName = if (version == "2") {
            val encoded = lines.single { it.first() == "N" }
            require(encoded.size == 2)
            requireNotNull(PlayerName.normalize(unhex(encoded[1]))) { "Invalid saved player name" }
        } else {
            require(lines.none { it.first() == "N" })
            "" // An older journey is resumed after asking for the user's first name.
        }
        val run = lines.single { it.first() == "R" }; require(run.size == 6)
        require(run[4] in setOf("English", "French") && run[5] in setOf("System", "Light", "Dark"))
        val amounts = lines.filter { it.first() == "A" }
        require(amounts.size == StartingField.entries.size && amounts.map { it[1] }.distinct().size == amounts.size)
        val data = StartingData.of(amounts.associate { require(it.size == 3); StartingField.valueOf(it[1]) to it[2].toLong() })
        val choices = lines.filter { it.first() == "C" }.map {
            require(it.size == 3); DecisionSelection(unhex(it[1]), unhex(it[2]))
        }
        val events = lines.filter { it.first() == "E" }.map { fields ->
            val id = unhex(fields[1]); val month = fields[2].toInt()
            when (fields[3]) {
                "rent" -> { require(fields.size == 6); LifeEvent.RentRise(id, month, MonthlyRentRule(unhex(fields[4]), month, fields[5].toDouble())) }
                "expense" -> { require(fields.size == 7); LifeEvent.OneOffExpense(id, month, unhex(fields[4]), Money(fields[5].toLong()), PaymentResponsibility.valueOf(fields[6])) }
                "maintenance" -> { require(fields.size == 6); LifeEvent.Maintenance(id, month, PaymentResponsibility.valueOf(fields[5]), Money(fields[4].toLong())) }
                "short" -> { require(fields.size == 6); LifeEvent.ShortInternshipOffer(id, month, fields[4].toInt(), Money(fields[5].toLong())) }
                "break" -> { require(fields.size == 5); LifeEvent.CareerBreak(id, month, fields[4].toInt()) }
                "epfl" -> { require(fields.size == 7); LifeEvent.EpflJobOffer(id, month,
                    Money(fields[4].toLong()), fields[5].toDouble(), fields[6].toInt()) }
                "closure" -> { require(fields.size == 5); LifeEvent.CompanyClosure(id, month, unhex(fields[4])) }
                "housing" -> { require(fields.size == 10); LifeEvent.HousingChoice(id, month,
                    HousingContract(unhex(fields[4]), Money(fields[5].toLong()), fields[6].toInt(), HousingType.valueOf(fields[7])),
                    Money(fields[8].toLong()), PaymentResponsibility.valueOf(fields[9])) }
                else -> error("Unsupported event in save")
            }
        }
        require(events.size <= 5000)
        val base = requireNotNull(AdventureRun.restore(run[1].toLong(), data, events, choices, run[2].toInt(), playerName = playerName.ifEmpty { "Player" })) { "Recorded decisions cannot be restored" }
        val expected = lines.single { it.first() == "V" }.drop(1)
        require(expected == listOf(base.currentState.cash.cents.toString(), base.currentState.netWorth.cents.toString(),
            base.currentState.studyMonthsCompleted.toString(), base.currentState.accountsPayable.cents.toString())) { "Replayed balances differ" }
        val selected = lines.filter { it.first() == "D" }.map { require(it.size == 2); WhatIf.valueOf(it[1]) }.toSet()
        require(selected.all { AdventureRun.whatIfStates(base)[it] == WhatIfState.Available })
        val draft = if (selected.isEmpty()) null else WhatIfDraft(base, selected, data.internshipSalary)
        return RestoredJourney(data, draft?.session ?: base, draft, run[3].toBooleanStrict(), run[4], run[5], playerName)
    }

    private fun hex(value: String) = value.map { it.code.toString(16).padStart(4, '0') }.joinToString("")
    private fun unhex(value: String): String {
        require(value.length % 4 == 0 && value.length <= 8192)
        return value.chunked(4).map { it.toInt(16).toChar() }.joinToString("")
    }
    private fun checksum(value: String): String {
        var hash = 2166136261u
        value.forEach { hash = (hash xor it.code.toUInt()) * 16777619u }
        return hash.toString(16)
    }
}
