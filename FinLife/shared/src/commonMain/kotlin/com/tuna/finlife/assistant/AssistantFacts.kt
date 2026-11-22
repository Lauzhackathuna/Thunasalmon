package com.tuna.finlife.assistant

import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.AlternativeChoice
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.EventKind
import com.tuna.finlife.game.RandomTroubles
import com.tuna.finlife.game.RentRiseResult
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.kind
import com.tuna.finlife.game.percentLabel
import com.tuna.finlife.game.rentRiseResult
import com.tuna.finlife.simulation.EventApplication
import com.tuna.finlife.simulation.EventOutcome
import com.tuna.finlife.simulation.FinancialProjections
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.LifeStatus
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyComparisons
import com.tuna.finlife.simulation.MonthlyConfiguration
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.MonthlyRentRule
import com.tuna.finlife.simulation.MonthlySimulation
import com.tuna.finlife.simulation.MonthlyState
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions
import com.tuna.finlife.simulation.TwelveMonthProjection
import kotlin.math.abs

/** Everything the assistant may know about the run. Nothing here is computed by the model. */
internal class AssistantContext(
    val session: SimulationSession,
    val counterfactual: Counterfactual?,
    /** What-ifs switched on for the coming month, still reversible. */
    val whatIfs: Set<WhatIf>,
    val internshipSalary: Long,
    val currency: String,
    val playerName: String = "Player",
) {
    val monthsPlayed: Int get() = session.history.size
}

/**
 * Engine figures written out as plain English lines for the model. Months are numbered as the player sees
 * them (the first played month is 1). Amounts are the engine's own values: nothing is summed or estimated here
 * except where a line says so (a lowest value, a list of months).
 */
internal class FactSheet(val lines: List<String>) {
    val text: String get() = lines.joinToString("\n")
}

internal object AssistantFacts {
    /** The engine's projection length (FinancialProjections always simulates 12 months). */
    const val PROJECTION_MONTHS = 12

    /** Played months [from]..[to] (player numbering, both included). */
    fun playedMonths(context: AssistantContext, from: Int, to: Int): FactSheet {
        val session = context.session
        val events = session.calendar.associateBy { it.id }
        return FactSheet(buildList {
            add("Amounts are in ${context.currency}.")
            for (number in from..to) addAll(monthLines(context, session.history[number - 1], events))
        })
    }

    private fun monthLines(context: AssistantContext, record: MonthlyRecord, events: Map<String, LifeEvent>): List<String> {
        val c = context.currency
        val flow = record.flow
        val n = record.month + 1
        return buildList {
            add("Month $n:")
            add("- Income: ${m(flow.income, c)}" + parts(c,
                "student income from parents" to flow.studentIncome,
                "internship pay" to flow.internshipIncome,
                "salary" to flow.professionalIncome,
            ) + ".")
            add("- Expenses: ${m(flow.expenses, c)}" + parts(c,
                "rent" to flow.rent,
                "health insurance" to flow.healthInsurance,
                "groceries" to flow.groceries,
                "other expenses" to flow.otherExpenses,
                "tuition" to flow.tuition,
                "unexpected costs" to flow.exceptionalExpenses,
                "decision costs" to flow.decisionCosts,
                "interest" to flow.interest,
                "taxes on salary" to flow.taxes,
            ) + ".")
            add("- Net worth: ${m(record.before.netWorth, c)} at the start, ${m(record.after.netWorth, c)} at the end (change ${m(record.netWorthChange, c)}).")
            add("- Balance (cash minus unpaid bills): ${m(record.before.theoreticalCash, c)} at the start, ${m(record.after.theoreticalCash, c)} at the end.")
            add(if (record.financingNeed > Money.ZERO) "- Unpaid bills at the end of the month: ${m(record.financingNeed, c)}."
                else "- No unpaid bills at the end of the month.")
            for (outcome in record.events) {
                val event = events[outcome.eventId] ?: continue
                eventLine(event, outcome, record, c)?.let { add("- $it") }
            }
        }
    }

    private fun eventLine(event: LifeEvent, outcome: EventOutcome, record: MonthlyRecord, c: String): String? {
        val applied = outcome.application == EventApplication.APPLIED
        return when (val kind = event.kind()) {
            is EventKind.RentRise -> when (rentRiseResult(kind.contractId, outcome, record)) {
                RentRiseResult.Applied -> "Rent rise of ${percentLabel(kind.annualExtraRate)}% per year on the flat Maya rents: she stayed and accepted the new rent."
                RentRiseResult.MovedInstead -> "Rent rise announced: Maya moved to the shared flat instead."
                RentRiseResult.AlreadyRising, RentRiseResult.PreviousHome -> "A rent rise was announced but did not apply."
            }
            is EventKind.Maintenance -> if (applied) "Unexpected maintenance bill: ${m(outcome.chargedCost, c)} charged." else null
            is EventKind.ShortInternship -> when {
                applied && outcome.optionId == "accept" -> "Maya accepted the ${kind.months}-month internship offer at ${m(kind.monthlyIncome, c)} per month."
                outcome.optionId == "decline" -> "Maya declined the ${kind.months}-month internship offer."
                else -> "The internship offer did not apply."
            }
            is EventKind.Internship -> if (applied && outcome.optionId == "accept")
                "Maya accepted a ${kind.months}-month internship at ${m(kind.monthlyIncome, c)} per month." else "Maya continued her studies instead of an internship."
            is EventKind.Housing -> when {
                applied && outcome.optionId == "move" -> "Maya moved to the shared flat: rent ${m(kind.newRent, c)} per month, moving cost ${m(outcome.chargedCost, c)}."
                outcome.optionId == "stay" -> "Maya stayed in the flat she rents instead of moving."
                else -> "The move did not apply."
            }
            is EventKind.OneOffExpense -> if (applied) "One-off expense: ${m(outcome.chargedCost, c)} charged." else null
            is EventKind.CareerBreak -> if (applied) "Maya began a ${kind.months}-month unpaid work break; ordinary bills still apply." else null
            is EventKind.EpflJobOffer -> when {
                applied && outcome.optionId == "epfl" -> "Maya accepted the fictional EPFL job at ${m(kind.salary, c)} per month."
                outcome.optionId == "stay" -> "Maya stayed in the private job; a fresh, independent ${percentLabel(kind.monthlyRisk)}% chance of company collapse is drawn each month while that job remains active."
                else -> "The EPFL offer did not apply."
            }
            EventKind.CompanyClosure -> if (applied) "The private company collapsed; Maya lost the job and future salary, but not existing savings." else null
            is EventKind.Financing -> if (applied) "Financing: borrowed ${m(kind.borrowing, c)}, repaid ${m(kind.principalRepayment, c)}." else null
            is EventKind.Plant, is EventKind.Oven, is EventKind.Purchase -> if (applied) "A purchase decision cost ${m(outcome.chargedCost, c)}." else null
        }
    }

    /** The situation at the opening of the next month to play. */
    fun current(context: AssistantContext): FactSheet {
        val session = context.session
        val state = session.currentState
        val c = context.currency
        val played = context.monthsPlayed
        return FactSheet(buildList {
            add("Amounts are in ${c}.")
            add(if (played == 0) "No month has been played yet; the next month to play is month 1."
                else "Months played: $played (months 1 to $played). The next month to play is month ${played + 1}.")
            add("Balance now (cash minus unpaid bills): ${m(state.theoreticalCash, c)}. Cash on the account: ${m(state.cash, c)}.")
            add(if (state.unpaid.items.isEmpty()) "No unpaid bills now."
                else "Unpaid bills now: ${m(Money(state.cash.cents - state.theoreticalCash.cents), c)}.")
            add("Net worth now: ${m(state.netWorth, c)}.")
            addAll(statusLines(state, c))
            add(when (state.housing.type) {
                HousingType.SHARED -> "Housing: shared flat."
                else -> "Housing: Maya rents a flat on her own (she is a tenant; she does not own it)."
            })
            session.history.lastOrNull()?.let { last ->
                add("Last played month (month ${last.month + 1}): income ${m(last.flow.income, c)}, expenses ${m(last.flow.expenses, c)}, rent ${m(last.flow.rent, c)}.")
            }
            state.rentRule?.takeIf { it.contractId == state.housing.id }?.let { rule ->
                add("The rent of the flat Maya rents rises by ${percentLabel(rule.annualExtraRate)}% per year since month ${rule.startMonth + 1}.")
            }
            if (context.whatIfs.isNotEmpty()) {
                add("What-ifs switched on for month ${played + 1} (not played yet): ${context.whatIfs.joinToString { whatIfName(it) }}.")
            }
            val pending = SimulationSessions.pendingDecisions(session)
            if (pending.isNotEmpty()) add("A decision is waiting: ${pending.joinToString { eventName(it.event) }}.")
        })
    }

    private fun statusLines(state: MonthlyState, c: String): List<String> = buildList {
        when (state.status) {
            LifeStatus.STUDENT, LifeStatus.INTERN -> add("Maya is a student; ${state.studyMonthsRemaining} months of studies remain.")
            LifeStatus.EMPLOYED -> when {
                state.jobLost -> add("Maya lost the private-sector job. Professional income is zero; existing savings remain.")
                state.careerBreakRemaining > 0 -> add("Maya is taking an unpaid career break; ${state.careerBreakRemaining} months of salary-free time remain.")
                else -> add("Maya has finished her studies and works since month ${(state.careerStartMonth ?: 0) + 1}.")
            }
        }
        if (state.status == LifeStatus.EMPLOYED && !state.jobLost) {
            add("Current employer: ${state.employerType}. The EPFL job pays 4500 CHF per month; the private salary follows the entered financial profile.")
        }
        state.shortInternship?.takeIf { it.active(state.month) || state.month < it.startMonth }?.let {
            add("Maya's 3-month internship runs from month ${it.startMonth + 1} to month ${it.startMonth + 3}, paying ${m(it.monthlyIncome, c)} per month.")
        }
    }

    /** The player's path against the dotted path, at the latest month both reached. */
    fun latestChoice(context: AssistantContext, counterfactual: Counterfactual): FactSheet {
        val main = context.session
        val other = counterfactual.alternative
        val c = context.currency
        val month = minOf(main.currentMonth, other.currentMonth)
        val comparison = MonthlyComparisons.compare(main, other, month)
        val mine = main.states[month]
        val theirs = other.states[month]
        return FactSheet(buildList {
            add("Amounts are in ${c}.")
            add(when (val choice = counterfactual.choice) {
                is AlternativeChoice.WithoutWhatIfs ->
                    "Latest choice: in month ${counterfactual.month + 1} Maya switched on ${choice.whatIfs.joinToString { whatIfName(it) }}. " +
                        "The dotted line is the same run without it" +
                        (if (WhatIf.SharedFlat in choice.whatIfs) " (on the dotted line Maya stays in the flat she rents alone)" else "") +
                        (if (WhatIf.Internship in choice.whatIfs) " (on the dotted line Maya does not do the internship)" else "") + "."
                is AlternativeChoice.OtherAnswer -> {
                    val answer = main.selections.firstOrNull { it.eventId == choice.event.id }?.optionId
                    "Latest choice: in month ${counterfactual.month + 1}, for ${eventName(choice.event)}, Maya chose " +
                        "\"${answer?.let { optionName(choice.event, it) } ?: "an answer"}\". " +
                        "The dotted line is the same run with \"${optionName(choice.event, choice.option.id)}\" instead."
                }
            })
            add("Both lines use the same engine and the same random events; the dotted line never goes beyond the months played.")
            if (month == 0) {
                add("No month has been played since the choice yet, so both lines are still equal.")
            } else {
                add("At the end of month $month:")
                add("- Net worth: Maya's path ${m(mine.netWorth, c)}, dotted line ${m(theirs.netWorth, c)}. " +
                    gap("Maya's path has", comparison.nominalNetWorth, "net worth than the dotted line", c))
                add("- Balance: Maya's path ${m(mine.theoreticalCash, c)}, dotted line ${m(theirs.theoreticalCash, c)}.")
                add("- Unpaid bills: Maya's path ${m(Money(mine.cash.cents - mine.theoreticalCash.cents), c)}, dotted line ${m(Money(theirs.cash.cents - theirs.theoreticalCash.cents), c)}.")
                add("- Since the start: " + gap("Maya's path received", comparison.income, "income than the dotted line", c) +
                    " " + gap("Maya's path spent", comparison.expenses, "than the dotted line", c))
            }
            if (other.currentMonth < main.currentMonth) {
                add("The dotted line stopped at month ${other.currentMonth}: it reached a decision with no automatic answer.")
            }
        })
    }

    /** The decisions waiting now, each option with the engine's preview of the month. */
    fun pendingDecisions(context: AssistantContext): FactSheet {
        val session = context.session
        val c = context.currency
        val month = session.currentMonth + 1
        return FactSheet(buildList {
            add("Amounts are in ${c}.")
            for (decision in SimulationSessions.pendingDecisions(session)) {
                add("Decision waiting in month $month: ${eventName(decision.event)}.")
                eventDetails(decision.event, session.configuration, c)?.let { add(it) }
                for (option in decision.options) {
                    val preview = AdventureRun.preview(session, decision.event.id, option.id)
                    add("- Option \"${optionName(decision.event, option.id)}\": immediate cost ${m(option.immediateCost, c)}." +
                        (preview?.let { r ->
                            " Month $month would end with income ${m(r.flow.income, c)}, expenses ${m(r.flow.expenses, c)}, " +
                                "balance ${m(r.after.theoreticalCash, c)}, net worth ${m(r.after.netWorth, c)}" +
                                (if (r.financingNeed > Money.ZERO) ", unpaid bills ${m(r.financingNeed, c)}." else ", no unpaid bills.")
                        } ?: ""))
                }
            }
        })
    }

    private fun eventDetails(event: LifeEvent, configuration: MonthlyConfiguration, c: String): String? = when (event) {
        is LifeEvent.RentRise -> {
            val shared = MonthlySimulation.sharedHousingChoice(event.month, configuration)
            "The rent of the flat Maya rents would rise by ${percentLabel(event.rule.annualExtraRate)}% per year. " +
                "Moving instead goes to the shared flat at ${m(shared.newContract.monthlyRent, c)} per month."
        }
        is LifeEvent.ShortInternshipOffer -> "The offer lasts ${event.durationMonths} months at ${m(event.monthlyIncome, c)} per month, alongside studies."
        is LifeEvent.HousingChoice -> "The new rented home costs ${m(event.newContract.monthlyRent, c)} per month; moving costs ${m(event.movingCost, c)}."
        is LifeEvent.EpflJobOffer -> "Fictional EPFL salary: ${m(event.monthlySalary, c)} per month; keeping the private-sector job means an independent ${percentLabel(event.monthlyFailureProbability)}% chance of company collapse EACH month, continuing until that company closes. This is a hypothetical game assumption, not an actual EPFL or company statistic."
        else -> null
    }

    /**
     * The engine's projection from now under its own assumptions (no random events, nothing changes), plus
     * its comparison of staying or moving and accepting or declining the 3-month offer. The engine always
     * simulates 12 months; only the first [months] (1-12) are given. Null if the engine cannot project here.
     */
    fun projection(context: AssistantContext, months: Int = PROJECTION_MONTHS): FactSheet? {
        require(months in 1..PROJECTION_MONTHS)
        val session = context.session
        val state = session.currentState
        if (state.month + 12 > MonthlyConfiguration.MAX_MONTHS) return null
        val c = context.currency
        val reference = try {
            FinancialProjections.nothingChanges(session)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        }
        val first = state.month + 1
        val last = state.month + months
        return FactSheet(buildList {
            add("Amounts are in ${c}.")
            add("Projection by the engine for the next $months months, months $first to $last, starting from the current situation. It is a simulation under these assumptions, not a prediction:")
            ProjectionAssumptions.english(session.configuration.annualInflation).forEach { add("- $it") }
            add(if (state.housing.type == HousingType.SHARED) "Maya lives in the shared flat now." else "Maya rents a flat on her own now (she is a tenant, not an owner; not a shared flat).")
            add("If nothing changes:")
            addAll(projectionLines(reference, months, c))
            val scenarios = studiedScenarios(context)
            if (scenarios != null && scenarios.size >= 2) {
                add("The engine also compared these alternatives over the same $months months (choices made in month $first):")
                for ((label, projection) in scenarios) {
                    add("$label:")
                    addAll(projectionLines(projection, months, c))
                }
            }
        })
    }

    /** The first [months] months of the engine's 12-month [projection]. */
    private fun projectionLines(projection: TwelveMonthProjection, months: Int, c: String): List<String> {
        val states = projection.states.take(months + 1)
        val end = states.last()
        val lowest = states.drop(1).minBy { it.theoreticalCash.cents }
        val deficit = projection.records.take(months).filter { it.needsFinancing }.map { it.month + 1 }
        return listOf(
            "- End of month ${end.month}: net worth ${m(end.netWorth, c)}.",
            "- End of month ${end.month}: balance ${m(end.theoreticalCash, c)}.",
            if (end.unpaid.items.isEmpty()) "- End of month ${end.month}: no unpaid bills."
            else "- End of month ${end.month}: unpaid bills owed ${m(Money(end.cash.cents - end.theoreticalCash.cents), c)} (an amount owed, separate from net worth and balance).",
            "- Lowest balance: ${m(lowest.theoreticalCash, c)} at the end of month ${lowest.month}.",
            if (deficit.isEmpty()) "- No month ends with unpaid bills."
            else "- Months ending with unpaid bills: ${deficit.joinToString()}.",
        )
    }

    /** The engine's stay/move × accept/decline comparison, with English labels. Null when not applicable. */
    internal fun studiedScenarios(context: AssistantContext): List<Pair<String, TwelveMonthProjection>>? {
        val session = context.session
        val state = session.currentState
        val known = session.calendar.filter { it.month >= state.month && !RandomTroubles.isRandom(it) && it !is LifeEvent.CompanyClosure }
        // A move or an offer already lined up for this month is a choice already made: no comparison then.
        if (known.any { it is LifeEvent.HousingChoice || it is LifeEvent.ShortInternshipOffer || it is LifeEvent.PurchaseChoice }) return null
        val offerPossible = state.status == LifeStatus.STUDENT &&
            state.shortInternship?.let { it.startMonth + 3 > state.month } != true
        val offer = if (offerPossible) {
            LifeEvent.ShortInternshipOffer("assistant-offer-${state.month}", state.month, monthlyIncome = Money.chf(context.internshipSalary))
        } else null
        val comparison = try {
            FinancialProjections.compareDecisions(session.profile, session.configuration, state, known, offer)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        }
        return comparison.scenarios.map { scenarioLabel(it.label, offer != null) to it }
    }

    /** English label for the engine's French scenario label ("Colocation, stage accepté", …). */
    internal fun scenarioLabel(engineLabel: String, offerStudied: Boolean): String {
        val housing = if (engineLabel.startsWith("Colocation")) "Shared flat" else "Current rented flat, alone"
        val internship = when {
            engineLabel.contains("stage accepté") -> "with the 3-month internship"
            offerStudied -> "without the internship"
            else -> null
        }
        return listOfNotNull(housing, internship).joinToString(", ")
    }

    /** Definitions of what the app shows, taken from the engine and the app's own rules. */
    fun concepts(context: AssistantContext): FactSheet {
        val configuration = context.session.configuration
        val c = context.currency
        val shared = MonthlySimulation.sharedHousingChoice(context.session.currentMonth, configuration)
        val rentRise = MonthlyRentRule("probe", 0).annualExtraRate
        return FactSheet(listOf(
            "Amounts are in ${c}.",
            "FinLife simulates the budget of Maya, a fictional student, month by month, with synthetic data.",
            "Maya is a tenant: she rents her housing and does not own it.",
            "Net worth = cash + investments − debt − unpaid bills. The chart shows net worth.",
            "Balance = cash on the account minus unpaid bills; it can go below zero. Cash itself never goes below zero: what cannot be paid stays as unpaid bills, and there is no automatic loan.",
            "Prices are indexed at ${percentLabel(configuration.annualInflation)}% inflation per year.",
            if (configuration.incomeTaxRate > 0.0) "Once Maya earns a salary, ${percentLabel(configuration.incomeTaxRate)}% of it goes to taxes and social contributions (a flat synthetic rate). Income from parents and internship pay are not taxed."
            else "Taxes are not modelled.",
            "Random problems are drawn each month: a rent rise with probability 1 in ${RandomTroubles.RENT_ODDS}, at most ${RandomTroubles.MAX_RENT_RISES_PER_BLOCK} per ${RandomTroubles.BLOCK_MONTHS} months, raising the rent by ${percentLabel(rentRise)}% per year; " +
                "and a maintenance bill of ${m(RandomTroubles.MAINTENANCE_COST, c)} with probability 1 in ${RandomTroubles.MAINTENANCE_ODDS}, at most ${RandomTroubles.MAX_MAINTENANCE_PER_BLOCK} per ${RandomTroubles.BLOCK_MONTHS} months.",
            "What-ifs can include a 3-month internship, moving into a shared flat, or (after finding work) a six-month unpaid career break. Ordinary expenses continue during the break; salary resumes afterwards unless the job was independently lost.",
            "After at least three months of employment, the fictional EPFL offer is 4500 CHF per month and modeled as stable. Keeping the private-sector job triggers a NEW independent 10% company-failure draw every month while the job exists, with no twelve-month cutoff. This is a hypothetical assumption, not real company data. Job loss stops future salary, never removes savings.",
            "The dotted line on the chart is the same run with the other side of the latest choice, simulated by the same engine with the same random events. It never goes beyond the months played.",
            "The projection is the engine's simulation of up to the next 12 months if nothing changes, with no random events. It is not a prediction, and nothing beyond 12 months ahead is simulated.",
            "FinLife is a simulation, not financial advice.",
        ))
    }

    /** Short context for routing; no amounts. */
    fun routingSummary(context: AssistantContext): String {
        val played = context.monthsPlayed
        return buildList {
            add(if (played == 0) "No month has been played yet." else "Months played: 1 to $played. The latest played month is $played.")
            add("The next month to play is month ${played + 1}.")
            add(if (context.counterfactual != null) "The chart shows a dotted line for the latest choice." else "The chart shows no dotted line.")
            add(if (SimulationSessions.pendingDecisions(context.session).isNotEmpty()) "A decision is waiting now." else "No decision is waiting.")
        }.joinToString(" ")
    }

    private fun whatIfName(whatIf: WhatIf) = when (whatIf) {
        WhatIf.Internship -> "the 3-month internship"
        WhatIf.SharedFlat -> "the move to the shared flat"
        WhatIf.CareerBreak -> "the unpaid six-month career break"
    }

    private fun eventName(event: LifeEvent): String = when (event) {
        is LifeEvent.RentRise -> "a rent rise"
        is LifeEvent.ShortInternshipOffer -> "a 3-month internship offer"
        is LifeEvent.Internship -> "an internship offer"
        is LifeEvent.HousingChoice -> "a move to the shared flat"
        is LifeEvent.Maintenance -> "a maintenance bill"
        is LifeEvent.PurchaseChoice -> "a purchase"
        is LifeEvent.OneOffExpense -> "a one-off expense"
        is LifeEvent.Financing -> "a financing"
        is LifeEvent.CareerBreak -> "a six-month career break"
        is LifeEvent.EpflJobOffer -> "an EPFL job offer"
        is LifeEvent.CompanyClosure -> "a private-company collapse"
    }

    private fun optionName(event: LifeEvent, optionId: String): String = when (event) {
        is LifeEvent.RentRise -> if (optionId == "move") "move to the shared flat" else "stay in her rented flat and accept the new rent"
        is LifeEvent.HousingChoice -> if (optionId == "move") "move" else "stay in her rented flat"
        is LifeEvent.ShortInternshipOffer, is LifeEvent.Internship -> if (optionId == "accept") "accept the internship" else "decline the internship"
        is LifeEvent.EpflJobOffer -> if (optionId == "epfl") "accept the stable EPFL job at 4500 CHF per month" else "stay in the higher-paid private job with a 10% company-failure chance each month"
        else -> if (optionId == "first") "first option" else "second option"
    }

    /** "Maya's path has 300 CHF more net worth than…" / "… 300 CHF less …" / "… the same …": no signed amounts. */
    private fun gap(subject: String, difference: Money, rest: String, c: String): String = when {
        difference > Money.ZERO -> "$subject ${m(difference, c)} more $rest."
        difference < Money.ZERO -> "$subject ${m(-difference, c)} less $rest."
        else -> "No difference: ${subject.removePrefix("Maya's path ")} the same on both lines."
    }

    private fun parts(c: String, vararg items: Pair<String, Money>): String {
        val shown = items.filter { it.second != Money.ZERO }
        return if (shown.isEmpty()) "" else " (" + shown.joinToString { "${it.first} ${m(it.second, c)}" } + ")"
    }
}

/** Engine amount as "1200 CHF", "1001.67 CHF" or "-300 CHF": no grouping, so the reply check reads it unambiguously. */
internal fun m(money: Money, currency: String): String {
    val cents = abs(money.cents)
    val text = if (cents % 100 == 0L) "${cents / 100}" else "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    return (if (money.cents < 0) "-" else "") + "$text $currency"
}
