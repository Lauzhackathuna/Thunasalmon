package com.tuna.finlife.game

import com.tuna.finlife.i18n.formatCents
import com.tuna.finlife.simulation.*
import kotlin.test.*

class ProductUpgradeTest {
    private fun clean() = SimulationSessions.start(configuration = MonthlyConfiguration(
        annualInflation = 0.0, annualSalaryGrowth = 0.0, randomEvents = RandomEventSettings(enabled = false)))

    @Test fun fourFutureChoicesUseActualTrajectoriesAndNeverChangeHistory() {
        val base = clean()
        val outlook = AdventureOutlook.calculate(base, 300)
        assertEquals(4, outlook.options.size)
        val expected = listOf(0L, 100L, 1600L, 2500L)
        assertEquals(expected.map { Money.chf(it) }, outlook.options.map { it.at(12).cash })
        assertEquals(Money.chf(800), outlook.baseline.stability.finalUnpaid)
        assertEquals(0, base.currentMonth)
        assertTrue(base.selections.isEmpty() && base.calendar.isEmpty())
        outlook.options.forEach { option ->
            assertEquals(base.currentState, option.at(0))
            assertEquals(13, option.projection.states.size)
            for (record in option.projection.records) {
                assertEquals(record.after.cash, record.after.envelopes.total)
                assertTrue(record.after.cash >= Money.ZERO)
                assertEquals(record.before.cash + record.flow.netCashFlow - record.flow.investmentContribution, record.after.cash)
                assertEquals(record.flow.netWorthChange, record.netWorthChange)
            }
        }
        val both = outlook.options.last()
        assertEquals(Money.chf(900), outlook.impact(both).income)
        assertEquals(Money.chf(2400), outlook.impact(both).rentSaved)
        assertEquals(FinancialStability.FRAGILE, both.stability.category)
    }

    @Test fun repeatedTogglingNeverDuplicatesAnEventOrAdvancesTime() {
        val base = clean()
        var draft = WhatIfDraft(base)
        repeat(10) {
            draft = draft.toggle(WhatIf.Internship, true).toggle(WhatIf.SharedFlat, true)
            assertEquals(2, draft.session.calendar.size)
            assertEquals(0, draft.session.currentMonth)
            draft = draft.toggle(WhatIf.Internship, false).toggle(WhatIf.SharedFlat, false)
            assertSame(base, draft.session)
        }
        draft = draft.toggle(WhatIf.Internship, true).toggle(WhatIf.SharedFlat, true)
        val preview = AdventureOutlook.calculate(base, 300).selected(draft.selected)
        val next = SimulationSessions.advance(draft.session)
        assertEquals(1, next.currentMonth)
        assertEquals(preview.at(1), next.currentState)
    }

    @Test fun currentStateProjectionRetainsActiveStageWithoutOfferingADuplicate() {
        val base = SimulationSessions.advance(WhatIfDraft(clean()).toggle(WhatIf.Internship, true).session)
        val outlook = AdventureOutlook.calculate(base, 300)
        assertEquals(2, outlook.options.size)
        assertTrue(outlook.options.all { WhatIf.Internship !in it.choices })
        assertEquals(1, outlook.baseline.at(0).month)
        assertEquals(13, outlook.baseline.at(12).month)
        assertEquals(Money.chf(600), outlook.baseline.projection.records.fold(Money.ZERO) { a, r -> a + r.flow.internshipIncome })
        val completed = SimulationSessions.runUntilBlocked(base, 2)
        assertEquals(4, AdventureOutlook.calculate(completed, 300).options.size)
    }

    @Test fun knownCurrentRepairIsSharedButNoFutureRandomDisasterIsGenerated() {
        val known = LifeEvent.OneOffExpense("random-maintenance-0", 0, "Minor repair", Money.chf(400), PaymentResponsibility.CHARACTER)
        val base = SimulationSessions.schedule(clean(), known)
        val outlook = AdventureOutlook.calculate(base, 300)
        outlook.options.forEach {
            assertEquals(Money.chf(400), it.projection.records.fold(Money.ZERO) { a, r -> a + r.flow.exceptionalExpenses })
            assertEquals(listOf("random-maintenance-0"), it.projection.records.flatMap { r -> r.events }.filter { e -> e.eventId.startsWith("random-") }.map { e -> e.eventId })
        }
    }

    @Test fun cashNetWorthAndTheoreticalAreDifferentWhenAssetsAndPrincipalExist() {
        val unpaid = UnpaidObligations(listOf(FinancialObligation("rent", 0, Envelope.HOUSING, Money.chf(50)),
            FinancialObligation("principal", 0, Envelope.OTHER, Money.chf(20), bankPrincipal = true)))
        val state = clean().currentState.copy(cash = Money.chf(100), envelopes = EnvelopeBalances(reserve = Money.chf(100)),
            investments = Money.chf(200), debt = Money.chf(80), unpaid = unpaid, realNetWorth = Money.chf(170))
        assertEquals(Money.chf(100), BalanceMetric.Cash.value(state))
        assertEquals(Money.chf(30), BalanceMetric.Theoretical.value(state))
        assertEquals(Money.chf(170), BalanceMetric.NetWorth.value(state))
    }

    @Test fun initialAllocationFillsPriorityBuffersForLowAndHighCash() {
        val p = MonthlyProfile.alexia()
        assertEquals(EnvelopeBalances.alexia(), BudgetEnvelopes.initial(p))
        val poor = BudgetEnvelopes.initial(p.copy(cash = Money.chf(100)))
        assertEquals(Money.chf(100), poor.studies)
        assertEquals(Money.ZERO, poor.reserve)
        for (cash in listOf(0L, 1L, 100L, 7999L, 8001L, 7500000L)) {
            val value = BudgetEnvelopes.initial(p.copy(cash = Money.chf(cash)))
            assertEquals(Money.chf(cash), value.total)
            assertTrue(Envelope.entries.all { value[it] >= Money.ZERO })
        }
        val rich = BudgetEnvelopes.initial(p.copy(cash = Money.chf(10000)))
        assertEquals(Money.chf(3350), rich.reserve)
        assertEquals(Money.ZERO, BudgetEnvelopes.initial(p.copy(studyMonths = 0)).studies)
    }

    @Test fun assumptionEditsReplayEachEditableFieldAndKeepDecisionsAndCalendar() {
        val start = AdventureRun.start(seed = 3)
        val original = playDefaults(WhatIfDraft(start).toggle(WhatIf.Internship, true).toggle(WhatIf.SharedFlat, true).session, 8)
        for (field in StartingField.entries) {
            val data = StartingData.of(StartingField.entries.associateWith { StartingData.Default[it] + if (it == field) 100 else 0 })
            val revised = assertNotNull(AdventureRun.recalculate(original, data))
            assertEquals(8, revised.currentMonth)
            assertEquals(original.selections.toSet(), revised.selections.toSet())
            assertEquals(original.calendar.map { it.id to it.month }, revised.calendar.map { it.id to it.month })
            assertEquals(revised.states, assertNotNull(AdventureRun.recalculate(original, data)).states)
            assertEquals(revised.currentState.cash, revised.currentState.envelopes.total)
        }
        assertEquals(Money.chf(300), original.history.first().flow.internshipIncome)
    }

    @Test fun invalidEditIsRejectedWithoutChangingTheOriginalRun() {
        val original = playDefaults(AdventureRun.start(seed = 3), 4)
        val history = original.states
        assertFailsWith<IllegalArgumentException> { AdventureRun.recalculate(original, StartingData.Default.copy(openingCash = -1)) }
        assertEquals(history, original.states)
        assertEquals(StartingDataForm.Result.Invalid(FieldError.NotAWholeNumber), StartingDataForm.parse("12,50", StartingField.Rent))
        assertEquals(StartingDataForm.Result.Valid(12L), StartingDataForm.parse("00000000000000000000012", StartingField.Rent))
    }

    @Test fun exactMoneyFormattingHandlesCentsNegativesAndLargeValues() {
        assertEquals("−0.01", formatCents(-1))
        assertEquals("8’000", formatCents(800000))
        assertEquals("1 234,56", formatCents(123456, " ", ","))
        assertEquals("90’000’000’000", formatCents(Money.MAX_CENTS))
        assertEquals("0", formatCents(0))
    }

    @Test fun visibleRangesHandleOnePointLongHistoryAndFutureOffsets() {
        assertEquals(0..0, ChartScale.visibleRange(1, 3, false))
        assertEquals(1197..1200, ChartScale.visibleRange(1201, 3, false))
        assertEquals(0..3, ChartScale.visibleRange(13, 3, true))
        assertEquals(0..12, ChartScale.visibleRange(13, null, true))
        val axis = ChartScale.valueAxis(-Money.MAX_CENTS, Money.MAX_CENTS)
        assertTrue(axis.min <= -Money.MAX_CENTS && axis.max >= Money.MAX_CENTS)
        assertTrue(axis.ticks.size < 10)
    }

    @Test fun summaryReconcilesActualPaymentsAndReportsUnpaidSeparately() {
        val run = SimulationSessions.runUntilBlocked(clean(), 12)
        val summary = RunSummary.of(run)
        assertEquals(Money.ZERO, summary.cash)
        assertEquals(Money.chf(-800), summary.theoretical)
        assertEquals(Money.chf(800), summary.unpaid)
        assertEquals(Money.chf(22400), summary.totalPaid)
        assertEquals(summary.initialCash + summary.totalIncome - summary.totalPaid, summary.cash)
    }

    @Test fun saveRestoresHistoryCalendarDecisionsDraftAndSummaryMode() {
        val data = StartingData.Default.copy(internshipSalary = 475)
        val base = playDefaults(AdventureRun.start(seed = 3, data = data), 5)
        val draft = WhatIfDraft(answerDefaults(base), internshipSalary = data.internshipSalary).toggle(WhatIf.Internship, true)
        val saved = JourneySave.encode(data, draft.session, draft, true, "French", "Dark")
        val restored = JourneySave.decode(saved)
        assertEquals(draft.session.states, restored.session.states)
        assertEquals(draft.session.calendar, restored.session.calendar)
        assertEquals(draft.session.selections, restored.session.selections)
        assertEquals(draft.selected, restored.draft?.selected)
        assertEquals(data, restored.data)
        assertTrue(restored.ended)
        assertEquals("French", restored.language)
        assertEquals("Dark", restored.theme)
        assertEquals(AdventureRun.advance(draft.session).states, AdventureRun.advance(restored.session).states)
    }

    @Test fun customNameSurvivesSaveAndLegacySaveWithoutNameRestoresMaya() {
        val data = StartingData.Default.copy(firstName = "Zoé")
        val session = AdventureRun.start(seed = 3, data = data)
        val saved = JourneySave.encode(data, session, null, false, "French", "Dark")
        val restored = JourneySave.decode(saved)
        assertEquals("Zoé", restored.data.firstName)
        assertEquals("Zoé", restored.session.profile.name)
        assertEquals(session.states, restored.session.states)

        // A v1 file created before the name field had no N record.
        val legacyBody = saved.substringBeforeLast("\nHASH|")
            .lines().filterNot { it.startsWith("N|") }.joinToString("\n")
        var hash = 2166136261u
        legacyBody.forEach { hash = (hash xor it.code.toUInt()) * 16777619u }
        val legacy = JourneySave.decode("$legacyBody\nHASH|\${hash.toString(16)}")
        assertEquals("Maya", legacy.data.firstName)
        assertEquals("Maya", legacy.session.profile.name)

        assertFails { JourneySave.decode(saved.replace("N|", "N|xyz")) }
    }

    @Test fun saveRoundTripIncludesMoveRepairAndPendingDecision() {
        val start = AdventureRun.start(seed = 3)
        val moved = playDefaults(WhatIfDraft(start).toggle(WhatIf.SharedFlat, true).session, 12)
        val encoded = JourneySave.encode(StartingData.Default, moved, null, false, "English", "Light")
        val restored = JourneySave.decode(encoded)
        assertEquals(moved.states, restored.session.states)
        assertEquals(moved.calendar, restored.session.calendar)
        val pending = (1L..200L).map { AdventureRun.start(seed = it) }.first { it.status == SessionStatus.AWAITING_DECISION }
        val pendingRestored = JourneySave.decode(JourneySave.encode(StartingData.Default, pending, null, false, "English", "System"))
        assertEquals(SessionStatus.AWAITING_DECISION, pendingRestored.session.status)
        assertEquals(pending.calendar, pendingRestored.session.calendar)
    }

    @Test fun corruptOrUnsupportedSaveNeverProducesAPartialRun() {
        val encoded = JourneySave.encode(StartingData.Default, AdventureRun.start(seed = 3), null, false, "English", "System")
        assertFails { JourneySave.decode(encoded.replace("FINLIFE|1", "FINLIFE|99")) }
        assertFails { JourneySave.decode(encoded.dropLast(4)) }
        assertFails { JourneySave.decode("broken") }
        assertFails { JourneySave.decode(encoded.replace("OpeningCash|8000", "OpeningCash|-1")) }
    }

    @Test fun maximumValidStartingAmountsProduceTwelveDeterministicMonths() {
        val data = StartingData.of(StartingField.entries.associateWith { StartingData.MAX_ABS_CHF })
        val start = AdventureRun.start(seed = 3, data = data)
        val outlook = AdventureOutlook.calculate(start, data.internshipSalary)
        assertEquals(4, outlook.options.size)
        assertTrue(outlook.options.all { it.at(12).cash >= Money.ZERO })
    }
}
