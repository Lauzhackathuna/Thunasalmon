package com.tuna.finlife.ui

import androidx.compose.runtime.Composable
import com.tuna.finlife.game.EventKind
import com.tuna.finlife.game.RentRiseResult
import com.tuna.finlife.game.percentLabel
import com.tuna.finlife.game.rentRiseResult
import com.tuna.finlife.simulation.DecisionOption
import com.tuna.finlife.simulation.EventApplication
import com.tuna.finlife.simulation.EventOutcome
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRecord
import finlife.shared.generated.resources.event_minor_repair
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.decision_rent_rise_prompt
import finlife.shared.generated.resources.decision_rent_rise_title
import finlife.shared.generated.resources.decision_short_internship_prompt
import finlife.shared.generated.resources.decision_short_internship_title
import finlife.shared.generated.resources.option_rent_rise_move
import finlife.shared.generated.resources.option_rent_rise_stay
import finlife.shared.generated.resources.option_short_internship_accept
import finlife.shared.generated.resources.option_short_internship_decline
import finlife.shared.generated.resources.outcome_rent_rise_moved
import finlife.shared.generated.resources.outcome_short_internship_accepted
import finlife.shared.generated.resources.outcome_short_internship_declined
import finlife.shared.generated.resources.outcome_short_internship_not_applicable
import finlife.shared.generated.resources.decision_generic_prompt
import finlife.shared.generated.resources.decision_housing_prompt
import finlife.shared.generated.resources.decision_housing_title
import finlife.shared.generated.resources.decision_internship_prompt
import finlife.shared.generated.resources.decision_internship_title
import finlife.shared.generated.resources.decision_oven_prompt
import finlife.shared.generated.resources.decision_oven_title
import finlife.shared.generated.resources.decision_plant_prompt
import finlife.shared.generated.resources.decision_plant_title
import finlife.shared.generated.resources.option_housing_move
import finlife.shared.generated.resources.option_housing_stay
import finlife.shared.generated.resources.option_internship_accept
import finlife.shared.generated.resources.option_internship_decline
import finlife.shared.generated.resources.option_oven_repair
import finlife.shared.generated.resources.option_oven_replace
import finlife.shared.generated.resources.option_plant_buy
import finlife.shared.generated.resources.option_plant_skip
import finlife.shared.generated.resources.outcome_financing
import finlife.shared.generated.resources.outcome_housing_moved
import finlife.shared.generated.resources.outcome_housing_stayed
import finlife.shared.generated.resources.outcome_internship_accepted
import finlife.shared.generated.resources.outcome_internship_declined
import finlife.shared.generated.resources.outcome_internship_not_applicable
import finlife.shared.generated.resources.outcome_maintenance
import finlife.shared.generated.resources.outcome_maintenance_other_party
import finlife.shared.generated.resources.outcome_one_off
import finlife.shared.generated.resources.outcome_other_party
import finlife.shared.generated.resources.outcome_oven_repaired
import finlife.shared.generated.resources.outcome_oven_replaced
import finlife.shared.generated.resources.outcome_plant_bought
import finlife.shared.generated.resources.outcome_plant_skipped
import finlife.shared.generated.resources.outcome_rent_rise
import finlife.shared.generated.resources.outcome_rent_rise_already_rising
import finlife.shared.generated.resources.outcome_rent_rise_previous_home
import org.jetbrains.compose.resources.stringResource

internal data class DecisionCopy(val title: String, val prompt: String)

/**
 * [month] is the zero-based engine month; it is shown one-based. [sharedRent] and [sharedMovingCost]
 * describe the engine's shared-flat alternative offered with a rent rise.
 */
@Composable
internal fun decisionCopy(
    kind: EventKind,
    month: Int,
    sharedRent: Money = Money.ZERO,
    sharedMovingCost: Money = Money.ZERO,
): DecisionCopy = when (kind) {
    is EventKind.RentRise -> DecisionCopy(
        playerStringResource(Res.string.decision_rent_rise_title),
        playerStringResource(
            Res.string.decision_rent_rise_prompt,
            percentLabel(kind.annualExtraRate),
            money(sharedRent),
            money(sharedMovingCost),
        ),
    )
    is EventKind.ShortInternship -> DecisionCopy(
        playerStringResource(Res.string.decision_short_internship_title),
        playerStringResource(Res.string.decision_short_internship_prompt, kind.months, money(kind.monthlyIncome)),
    )
    is EventKind.Internship -> DecisionCopy(
        playerStringResource(Res.string.decision_internship_title),
        playerStringResource(Res.string.decision_internship_prompt, kind.months, money(kind.monthlyIncome)),
    )
    is EventKind.Housing -> DecisionCopy(
        playerStringResource(Res.string.decision_housing_title),
        playerStringResource(Res.string.decision_housing_prompt, money(kind.newRent), money(kind.movingCost)),
    )
    is EventKind.Plant -> DecisionCopy(
        playerStringResource(Res.string.decision_plant_title),
        playerStringResource(Res.string.decision_plant_prompt, money(kind.cost)),
    )
    is EventKind.Oven -> DecisionCopy(
        playerStringResource(Res.string.decision_oven_title),
        playerStringResource(Res.string.decision_oven_prompt, money(kind.repairCost), money(kind.replacementCost)),
    )
    is EventKind.Purchase -> DecisionCopy(kind.title, playerStringResource(Res.string.decision_generic_prompt, month + 1))
    // Not interactive in the engine; listed for completeness.
    else -> DecisionCopy("", playerStringResource(Res.string.decision_generic_prompt, month + 1))
}

/** Engine option ids: accept/decline, stay/move, first/second. Unknown cases fall back to the engine label. */
@Composable
internal fun optionLabel(kind: EventKind, option: DecisionOption): String = when {
    kind is EventKind.Internship && option.id == "accept" -> playerStringResource(Res.string.option_internship_accept)
    kind is EventKind.Internship && option.id == "decline" -> playerStringResource(Res.string.option_internship_decline)
    kind is EventKind.ShortInternship && option.id == "accept" -> playerStringResource(Res.string.option_short_internship_accept)
    kind is EventKind.ShortInternship && option.id == "decline" -> playerStringResource(Res.string.option_short_internship_decline)
    kind is EventKind.RentRise && option.id == "stay" -> playerStringResource(Res.string.option_rent_rise_stay)
    kind is EventKind.RentRise && option.id == "move" -> playerStringResource(Res.string.option_rent_rise_move)
    kind is EventKind.Housing && option.id == "stay" -> playerStringResource(Res.string.option_housing_stay)
    kind is EventKind.Housing && option.id == "move" -> playerStringResource(Res.string.option_housing_move)
    kind is EventKind.Plant && option.id == "first" -> playerStringResource(Res.string.option_plant_buy)
    kind is EventKind.Plant && option.id == "second" -> playerStringResource(Res.string.option_plant_skip)
    kind is EventKind.Oven && option.id == "first" -> playerStringResource(Res.string.option_oven_repair)
    kind is EventKind.Oven && option.id == "second" -> playerStringResource(Res.string.option_oven_replace)
    else -> option.label
}

/** One sentence describing what an event did during [record]'s month, or null if nothing to say. */
@Composable
internal fun outcomeText(kind: EventKind, outcome: EventOutcome, record: MonthlyRecord): String? {
    val applied = outcome.application == EventApplication.APPLIED
    val notApplicable = outcome.application == EventApplication.NOT_APPLICABLE
    return when (kind) {
        is EventKind.Internship -> playerStringResource(
            when (outcome.application) {
                EventApplication.APPLIED -> Res.string.outcome_internship_accepted
                EventApplication.DECLINED -> Res.string.outcome_internship_declined
                EventApplication.NOT_APPLICABLE -> Res.string.outcome_internship_not_applicable
            },
        )
        is EventKind.RentRise -> when (rentRiseResult(kind.contractId, outcome, record)) {
            RentRiseResult.Applied -> playerStringResource(Res.string.outcome_rent_rise, percentLabel(kind.annualExtraRate))
            RentRiseResult.MovedInstead -> playerStringResource(Res.string.outcome_rent_rise_moved)
            RentRiseResult.PreviousHome -> playerStringResource(Res.string.outcome_rent_rise_previous_home)
            RentRiseResult.AlreadyRising -> playerStringResource(Res.string.outcome_rent_rise_already_rising)
        }
        is EventKind.ShortInternship -> playerStringResource(
            when (outcome.application) {
                EventApplication.APPLIED -> Res.string.outcome_short_internship_accepted
                EventApplication.DECLINED -> Res.string.outcome_short_internship_declined
                EventApplication.NOT_APPLICABLE -> Res.string.outcome_short_internship_not_applicable
            },
        )
        is EventKind.Maintenance ->
            if (applied) playerStringResource(Res.string.outcome_maintenance, money(outcome.chargedCost))
            else playerStringResource(Res.string.outcome_maintenance_other_party)
        is EventKind.Plant -> when {
            notApplicable -> playerStringResource(Res.string.outcome_other_party)
            outcome.optionId == "first" -> playerStringResource(Res.string.outcome_plant_bought)
            else -> playerStringResource(Res.string.outcome_plant_skipped)
        }
        is EventKind.Oven -> when {
            notApplicable -> playerStringResource(Res.string.outcome_other_party)
            outcome.optionId == "first" -> playerStringResource(Res.string.outcome_oven_repaired)
            else -> playerStringResource(Res.string.outcome_oven_replaced)
        }
        is EventKind.Purchase ->
            if (notApplicable) playerStringResource(Res.string.outcome_other_party)
            else playerStringResource(
                Res.string.outcome_one_off,
                kind.title,
                if (outcome.optionId == "first") kind.firstLabel else kind.secondLabel,
            )
        is EventKind.Housing -> when (outcome.application) {
            EventApplication.APPLIED -> playerStringResource(Res.string.outcome_housing_moved)
            EventApplication.DECLINED -> playerStringResource(Res.string.outcome_housing_stayed)
            EventApplication.NOT_APPLICABLE -> null
        }
        is EventKind.OneOffExpense ->
            if (applied && outcome.eventId.startsWith("random-maintenance-")) playerStringResource(Res.string.event_minor_repair, money(outcome.chargedCost))
            else if (applied) playerStringResource(Res.string.outcome_one_off, kind.label, money(outcome.chargedCost))
            else playerStringResource(Res.string.outcome_other_party)
        is EventKind.Financing ->
            playerStringResource(Res.string.outcome_financing, money(kind.borrowing), money(kind.principalRepayment))
    }
}
