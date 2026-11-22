package com.tuna.finlife.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplyGuardTest {
    private fun value(text: String) = ReplyGuard.numbers(text).single().second

    @Test
    fun readsGroupedAndDecimalNumbersInBothLanguages() {
        assertEquals(120_000, value("1'200 CHF"))
        assertEquals(120_000, value("1 200 CHF"))
        assertEquals(120_000, value("1 200 CHF"))
        assertEquals(120_050, value("1 200,50 CHF"))
        assertEquals(120_050, value("1,200.50 CHF"))
        assertEquals(120_000, value("1,200 CHF"))
        assertEquals(100_167, value("1001.67 CHF"))
        assertEquals(250, value("2.5%"))
        assertEquals(150, value("1,5 %"))
        assertEquals(30_000, value("−300 CHF"))
    }

    @Test
    fun acceptsFactNumbersExactlyOrRoundedToWholeUnits() {
        val facts = "Month 3: rent 1001.67 CHF, change -300 CHF, rise 3% per year."
        assertEquals(emptyList(), ReplyGuard.unsupportedNumbers("In month 3 the rent was 1'001.67 CHF (about 1002 CHF).", facts))
        assertEquals(emptyList(), ReplyGuard.unsupportedNumbers("Net worth fell by 300 CHF; the rent rises 3% a year.", facts))
        assertEquals(emptyList(), ReplyGuard.unsupportedNumbers("Elle n'a aucune facture impayée (0 CHF).", facts))
    }

    @Test
    fun flagsInventedOrApproximatedNumbers() {
        val facts = "Month 3: rent 1001.67 CHF, income 1200 CHF."
        assertEquals(listOf("1000"), ReplyGuard.unsupportedNumbers("Rent was roughly 1000 CHF.", facts))
        assertEquals(listOf("198.33"), ReplyGuard.unsupportedNumbers("She kept 198.33 CHF after rent.", facts))
        assertEquals(listOf("4"), ReplyGuard.unsupportedNumbers("In month 4 the income was 1200 CHF.", facts))
    }

    @Test
    fun findsAdviceInEnglishAndFrench() {
        assertEquals(listOf("you should"), ReplyGuard.advicePhrases("You should move to the shared flat."))
        assertEquals(listOf("je vous conseille"), ReplyGuard.advicePhrases("Je vous conseille d’accepter."))
        assertEquals(listOf("il vaut mieux"), ReplyGuard.advicePhrases("Il vaut mieux refuser le stage."))
        assertTrue(ReplyGuard.advicePhrases("The shared flat ends with more money in this simulation.").isEmpty())
    }
    @Test fun rejectsMetricSwapsAndSignChangesInBothLanguages() {
        val facts = "Salary: 6000 CHF. Net worth: -300 CHF. Balance (cash minus unpaid bills): -400 CHF. Cash on the account: 0 CHF. Unpaid bills: 400 CHF."
        for (reply in listOf("Net worth: 6000 CHF.", "Patrimoine net : 6000 CHF.",
            "Balance: 300 CHF.", "Net worth: 300 CHF.", "Factures impayées : 0 CHF.",
            "Solde : 6000 CHF.", "6000 CHF.")) {
            assertTrue(ReplyGuard.unsupportedMetricAmounts(reply, facts).isNotEmpty(), reply)
        }
        for (reply in listOf("Net worth: -300 CHF.", "Patrimoine net : −300 CHF.",
            "Solde : -400 CHF.", "Unpaid bills: 400 CHF.", "Salaire : 6 000 CHF.")) {
            assertTrue(ReplyGuard.unsupportedMetricAmounts(reply, facts).isEmpty(), reply)
        }
    }

}
