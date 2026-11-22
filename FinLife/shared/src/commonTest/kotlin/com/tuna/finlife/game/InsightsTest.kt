package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRentRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {
    private val fresh = answerDefaults(AdventureRun.start(seed = 5))
    private val played = playDefaults(fresh, 3)

    @Test
    fun assumptionsAreTheEngineValues() {
        val values = ModelAssumptions.of(played, internshipSalary = 300)
        val profile = played.profile
        val configuration = played.configuration
        assertEquals(profile.cash, values.openingCash)
        assertEquals(profile.studentMonthlyIncome, values.parentIncome)
        assertEquals(Money.chf(300), values.internshipMonthlyPay)
        assertEquals(profile.startingMonthlySalary, values.salaryAfterStudies)
        assertEquals(profile.housing.monthlyRent, values.rent)
        assertEquals(profile.tuitionPayment, values.tuition)
        assertEquals(profile.tuitionEveryStudyMonths, values.tuitionEveryMonths)
        assertEquals(profile.studyMonths, values.studyMonths)
        assertEquals(configuration.annualInflation, values.annualInflation)
        assertEquals(configuration.sharedReferenceRent, values.sharedFlatRent)
        assertEquals(configuration.goal, values.goal)
        assertEquals(MonthlyRentRule("x", 0).annualExtraRate, values.rentRiseAnnualRate)
        assertEquals(RandomTroubles.RENT_ODDS, values.rentRiseOdds)
        assertEquals(RandomTroubles.MAINTENANCE_COST, values.repairCost)
        // Values typed on the setup screen reach the engine and the screen alike.
        val custom = StartingData.Default.copy(rent = 1234, parentIncome = 999)
        val run = AdventureRun.start(seed = 5, data = custom)
        assertEquals(Money.chf(1234), ModelAssumptions.of(run, custom.internshipSalary).rent)
        assertEquals(Money.chf(999), ModelAssumptions.of(run, custom.internshipSalary).parentIncome)
    }

    @Test
    fun neutralSensitivityIsTheUnchangedOutlook() {
        val draft = WhatIfDraft(played)
        val unchanged = AdventureOutlook.calculate(draft.base, draft.internshipSalary)
        val neutral = AdventureOutlook.calculate(draft.base, draft.internshipSalary, Sensitivity())
        assertEquals(unchanged.options.map { it.projection.states }, neutral.options.map { it.projection.states })
    }

    @Test
    fun sensitivityChangesOnlyTheChosenInputs() {
        val draft = WhatIfDraft(played)
        val unchanged = AdventureOutlook.calculate(draft.base, draft.internshipSalary).baseline.projection.records.first().flow
        val changed = AdventureOutlook.calculate(draft.base, draft.internshipSalary, Sensitivity(rentPercent = 10, incomePercent = -20))
            .baseline.projection.records.first().flow
        assertEquals(unchanged.rent.scaled(1.1).cents.toDouble(), changed.rent.cents.toDouble(), absoluteTolerance = 2.0)
        assertEquals(unchanged.studentIncome.scaled(0.8), changed.studentIncome)
        assertEquals(unchanged.groceries, changed.groceries)
        assertEquals(unchanged.healthInsurance, changed.healthInsurance)
        // The run itself is untouched.
        assertEquals(played.currentState, draft.base.currentState)
    }

    @Test
    fun higherRentMeansLessNetWorthInTheOutlook() {
        val draft = WhatIfDraft(played)
        fun end(s: Sensitivity) = AdventureOutlook.calculate(draft.base, draft.internshipSalary, s).baseline.at(12).netWorth
        assertTrue(end(Sensitivity(rentPercent = 20)) < end(Sensitivity()))
        assertTrue(end(Sensitivity(incomePercent = 20)) > end(Sensitivity()))
    }

    @Test
    fun sensitivityStaysInItsRange() {
        assertFailsWith<IllegalArgumentException> { Sensitivity(rentPercent = 60) }
        assertFailsWith<IllegalArgumentException> { Sensitivity(incomePercent = -51) }
    }

    @Test
    fun breakdownAddsUpExactlyToTheGap() {
        val draft = WhatIfDraft(fresh).toggle(WhatIf.SharedFlat, true)
        var main = AdventureRun.advance(answerDefaults(draft.session))
        var counterfactual = Counterfactual.afterAdvance(null, draft, main)
        repeat(4) { main = AdventureRun.advance(answerDefaults(main)); counterfactual = counterfactual?.catchUp(main) }
        val breakdown = assertNotNull(DifferenceBreakdown.of(main, assertNotNull(counterfactual)))
        assertEquals(main.states[breakdown.month].netWorth - counterfactual!!.alternative.states[breakdown.month].netWorth, breakdown.total)
        assertEquals(breakdown.total, breakdown.effects.fold(Money.ZERO) { sum, effect -> sum + effect.effect })
        // The shared flat is cheaper: rent is the main reason Maya's path is ahead.
        assertEquals(FlowItem.Rent, breakdown.effects.first().item)
        assertTrue(breakdown.effects.first().effect > Money.ZERO)
        assertEquals(breakdown.effects.sortedByDescending { kotlin.math.abs(it.effect.cents) }, breakdown.effects)
    }

    @Test
    fun internshipShowsAsExtraPay() {
        val draft = WhatIfDraft(fresh).toggle(WhatIf.Internship, true)
        var main = AdventureRun.advance(answerDefaults(draft.session))
        var counterfactual = Counterfactual.afterAdvance(null, draft, main)
        repeat(3) { main = AdventureRun.advance(answerDefaults(main)); counterfactual = counterfactual?.catchUp(main) }
        val breakdown = assertNotNull(DifferenceBreakdown.of(main, assertNotNull(counterfactual)))
        val pay = breakdown.effects.single { it.item == FlowItem.InternshipPay }
        assertTrue(pay.effect > Money.ZERO)
        assertEquals(breakdown.total, breakdown.effects.fold(Money.ZERO) { sum, effect -> sum + effect.effect })
    }

    @Test
    fun noBreakdownBeforeAnyMonthIsPlayedOnBothPaths() {
        assertNull(DifferenceBreakdown.of(played, played, played.currentMonth))
    }
}
