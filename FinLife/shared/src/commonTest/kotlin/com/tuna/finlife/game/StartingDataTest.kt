package com.tuna.finlife.game

import com.tuna.finlife.game.StartingDataForm.Result
import com.tuna.finlife.simulation.DataProvenance
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartingDataTest {
    @Test
    fun parsesWholeFrancsWithGrouping() {
        assertEquals(Result.Valid(8_000L), StartingDataForm.parse(" 8’000 ", StartingField.ParentIncome))
        assertEquals(Result.Valid(12_500L), StartingDataForm.parse("12,500", StartingField.Rent))
        assertEquals(Result.Valid(0L), StartingDataForm.parse("0", StartingField.Groceries))
    }

    @Test
    fun noFieldMayBeNegativeBecauseTheEngineRejectsNegativeCash() {
        assertEquals(Result.Invalid(FieldError.Negative), StartingDataForm.parse("−500", StartingField.OpeningCash))
        assertEquals(Result.Invalid(FieldError.Negative), StartingDataForm.parse("-500", StartingField.Rent))
    }

    @Test
    fun rejectsEmptyDecimalsTextAndAmountsBeyondTheEngineBound() {
        assertEquals(Result.Invalid(FieldError.Empty), StartingDataForm.parse("  ", StartingField.Rent))
        assertEquals(Result.Invalid(FieldError.NotAWholeNumber), StartingDataForm.parse("12.50", StartingField.Rent))
        assertEquals(Result.Invalid(FieldError.NotAWholeNumber), StartingDataForm.parse("abc", StartingField.Rent))
        assertEquals(Result.Invalid(FieldError.NotAWholeNumber), StartingDataForm.parse("-", StartingField.Rent))
        assertEquals(7_500_000L, StartingData.MAX_ABS_CHF)
        assertEquals(Result.Valid(7_500_000L), StartingDataForm.parse("7500000", StartingField.Rent))
        assertEquals(Result.Invalid(FieldError.TooLarge), StartingDataForm.parse("7500001", StartingField.Rent))
        assertEquals(Result.Invalid(FieldError.TooLarge), StartingDataForm.parse("99999999999999999999", StartingField.Rent))
    }

    @Test
    fun buildNeedsEveryFieldValid() {
        val texts = StartingDataForm.texts(StartingData.Default)
        assertEquals(StartingData.Default, StartingDataForm.build(texts))
        val broken = texts + (StartingField.Groceries to "")
        assertNull(StartingDataForm.build(broken))
        assertEquals(mapOf(StartingField.Groceries to FieldError.Empty), StartingDataForm.errors(broken))
    }

    @Test
    fun defaultsAreTheEnginesReferenceValues() {
        val engine = com.tuna.finlife.simulation.MonthlyProfile.alexia()
        val configuration = com.tuna.finlife.simulation.MonthlyConfiguration()
        assertEquals(
            StartingData(8_000, 1_200, 1_000, 250, 250, 300, 800, 6_000, 800, 0),
            StartingData.Default,
        )
        assertEquals(engine.housing.monthlyRent, Money.chf(StartingData.Default.rent))
        assertEquals(configuration.sharedReferenceRent, Money.chf(StartingData.Default.sharedFlatRent))
        val run = AdventureRun.start(seed = 9)
        assertEquals(engine.cash, run.currentState.cash)
        assertEquals(engine.studentMonthlyIncome, run.profile.studentMonthlyIncome)
        assertEquals(DataProvenance.TEAM_ASSUMPTION, run.profile.provenance)
    }

    @Test
    fun editedValuesReachTheEngineAndTheWhatIf() {
        val data = StartingData(1_000, 900, 800, 300, 200, 100, 1_000, 5_000, 450, 250)
        val run = AdventureRun.start(seed = 9, data = data)
        assertEquals(Money.chf(1_000), run.currentState.cash)
        assertEquals(DataProvenance.USER_INPUT, run.profile.provenance)

        val month = AdventureRun.advance(WhatIfDraft(answerDefaults(run)).toggle(WhatIf.SharedFlat, true).session)
        val flow = month.history.single().flow
        assertEquals(Money.chf(900), flow.studentIncome)
        assertEquals(Money.chf(1_000), flow.tuition)
        assertEquals(Money.chf(300), flow.healthInsurance)
        assertEquals(Money.chf(200), flow.groceries)
        assertEquals(Money.chf(100), flow.otherExpenses)
        assertEquals(Money.chf(450), flow.rent)
        assertEquals(Money.chf(250), flow.decisionCosts)
        assertEquals(HousingType.SHARED, month.currentState.housing.type)

        val graduated = playDefaults(AdventureRun.start(seed = 9, data = data), 24)
        assertEquals(Money.chf(5_000), playDefaults(graduated, 1).history.last().flow.professionalIncome)
        assertTrue(assertNotNull(graduated.currentState.careerStartMonth) == 24)
    }
}
