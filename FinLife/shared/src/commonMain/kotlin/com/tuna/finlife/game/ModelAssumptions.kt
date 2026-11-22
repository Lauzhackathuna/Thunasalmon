package com.tuna.finlife.game

import com.tuna.finlife.simulation.FinancialGoal
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRentRule
import com.tuna.finlife.simulation.SimulationSession

/**
 * Every value the run is simulated with, read from the engine's profile and configuration (and the app's
 * random-problem rules). Nothing here is a second copy of a number: the assumptions screen shows exactly
 * what the engine uses.
 */
internal data class ModelAssumptions(
    val openingCash: Money,
    val parentIncome: Money,
    val internshipMonthlyPay: Money,
    val internshipMonths: Int,
    val salaryAfterStudies: Money,
    val annualSalaryGrowth: Double,
    val studyMonths: Int,
    val tuition: Money,
    val tuitionEveryMonths: Int,
    val rent: Money,
    val healthInsurance: Money,
    val groceries: Money,
    val otherExpenses: Money,
    val annualInflation: Double,
    val sharedFlatRent: Money,
    val sharedFlatMovingCost: Money,
    val rentRiseAnnualRate: Double,
    val rentRiseOdds: Int,
    val maxRentRisesPerBlock: Int,
    val repairCost: Money,
    val repairOdds: Int,
    val maxRepairsPerBlock: Int,
    val blockMonths: Int,
    val investmentShare: Double,
    val goal: FinancialGoal,
) {
    companion object {
        /** The internship offer lasts three months (engine: LifeEvent.ShortInternshipOffer). */
        const val INTERNSHIP_MONTHS = 3

        fun of(session: SimulationSession, internshipSalary: Long): ModelAssumptions {
            val profile = session.profile
            val configuration = session.configuration
            return ModelAssumptions(
                openingCash = profile.cash,
                parentIncome = profile.studentMonthlyIncome,
                internshipMonthlyPay = Money.chf(internshipSalary),
                internshipMonths = INTERNSHIP_MONTHS,
                salaryAfterStudies = profile.startingMonthlySalary,
                annualSalaryGrowth = configuration.annualSalaryGrowth,
                studyMonths = profile.studyMonths,
                tuition = profile.tuitionPayment,
                tuitionEveryMonths = profile.tuitionEveryStudyMonths,
                rent = profile.housing.monthlyRent,
                healthInsurance = profile.healthInsurance,
                groceries = profile.groceries,
                otherExpenses = profile.otherExpenses,
                annualInflation = configuration.annualInflation,
                sharedFlatRent = configuration.sharedReferenceRent,
                sharedFlatMovingCost = configuration.sharedMovingCost,
                rentRiseAnnualRate = MonthlyRentRule("probe", 0).annualExtraRate,
                rentRiseOdds = RandomTroubles.RENT_ODDS,
                maxRentRisesPerBlock = RandomTroubles.MAX_RENT_RISES_PER_BLOCK,
                repairCost = RandomTroubles.MAINTENANCE_COST,
                repairOdds = RandomTroubles.MAINTENANCE_ODDS,
                maxRepairsPerBlock = RandomTroubles.MAX_MAINTENANCE_PER_BLOCK,
                blockMonths = RandomTroubles.BLOCK_MONTHS,
                investmentShare = configuration.investmentShare,
                goal = configuration.goal,
            )
        }
    }
}
