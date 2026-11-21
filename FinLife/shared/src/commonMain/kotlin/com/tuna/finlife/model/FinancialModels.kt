package com.tuna.finlife.model

internal const val DEMO_HORIZON_MONTHS = 12

enum class Persona { MastersStudent, NewlyArrivedWorker, Apprentice }

enum class ScenarioId { S037, S039, S005 }

enum class GoalStatus { OnTrack, AtRisk, OffTrack }

enum class LifeEvent { TuitionDue, ArrivalCosts }

/** Month -1 is the opening balance. Month 0 is the first completed month. */
data class FinancialSnapshot(
    val monthIndex: Int,
    val cash: Double,
    val netWorth: Double,
    val liquidityMonths: Double,
    val goalFeasibility: GoalStatus,
    val constraintBreaches: Int,
)

data class MonthOutcome(
    val snapshot: FinancialSnapshot,
    val income: Double,
    val expenses: Double,
    val events: List<LifeEvent>,
)
