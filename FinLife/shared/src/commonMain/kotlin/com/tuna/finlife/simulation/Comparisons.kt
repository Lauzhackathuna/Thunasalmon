package com.tuna.finlife.simulation

data class StateDifference(val year: Int, val netWorth: Double, val realNetWorth: Double)
data class FlowDifference(val year: Int, val income: Double, val expenses: Double)
data class SimulationComparison(
    val selected: SimulationResult,
    val counterfactual: SimulationResult,
    val states: List<StateDifference>,
    val flows: List<FlowDifference>,
) {
    val finalNetWorthDifference: Double get() = states.last().netWorth
}

object SimulationComparisons {
    /** Runs both full trajectories; never sums standalone scenario effects. */
    fun compare(selected: SimulationParameters, counterfactual: SimulationParameters): SimulationComparison {
        require((selected.annualPlans?.size ?: selected.years) == (counterfactual.annualPlans?.size ?: counterfactual.years))
        val actual = FinancialSimulation.simulate(selected)
        val alternative = FinancialSimulation.simulate(counterfactual)
        return SimulationComparison(actual, alternative,
            actual.states.zip(alternative.states) { a, b -> StateDifference(a.year, a.netWorth - b.netWorth, a.realNetWorth - b.realNetWorth) },
            actual.flows.zip(alternative.flows) { a, b ->
                FlowDifference(a.year, a.income - b.income, a.expenses - b.expenses)
            })
    }

    fun effect(parameters: SimulationParameters, scenario: ScenarioId): SimulationComparison = compare(
        parameters, when (scenario) {
            ScenarioId.S005 -> parameters.copy(internship = false)
            ScenarioId.S037 -> parameters.copy(rentIncrease = parameters.rentIncrease.copy(enabled = false))
            ScenarioId.S040 -> parameters.copy(maintenance = parameters.maintenance.copy(enabled = false))
        },
    )

    /** Same economic assumptions and balances, with all three interventions disabled. */
    fun againstReference(parameters: SimulationParameters): SimulationComparison = compare(parameters,
        parameters.copy(internship = false, rentIncrease = parameters.rentIncrease.copy(enabled = false),
            maintenance = parameters.maintenance.copy(enabled = false)))

    /** Remove only S040 while retaining every other input and interaction. */
    fun maintenanceEffect(parameters: SimulationParameters): SimulationComparison = compare(
        parameters, parameters.copy(maintenance = parameters.maintenance.copy(enabled = false)),
    )
}

object FinancialExplanations {
    fun explain(parameters: SimulationParameters): List<String> {
        val a = parameters.assumptions
        return listOf(
            "Les métadonnées des scénarios proviennent de la bibliothèque UBS scenario-library-120.csv, selon les définitions fournies par l'équipe. Tous les chiffres sont des hypothèses de l'équipe, pas des chiffres officiels UBS.",
            if (parameters.annualPlans != null) "Plans nominaux personnalisés : ils remplacent le parcours de référence."
            else if (parameters.internship) "S005 actif : stage à t=0 (${a.internshipIncome} CHF), études à t=1 et t=2 (${a.studentIncome} CHF/an), emploi à t=3."
            else "S005 désactivé : études à t=0 et t=1 (${a.studentIncome} CHF/an), emploi à t=2.",
            "Salaire initial ${a.startingSalary} CHF, croissance nominale ${a.nominalSalaryGrowth * 100}% par année professionnelle, sans inflation supplémentaire.",
            "Inflation ${a.inflation * 100}% : loyers, dépenses essentielles et discrétionnaires, frais d'études indexés. Patrimoine réel = patrimoine nominal / (1 + inflation)^t.",
            if (parameters.rentIncrease.enabled) "S037 actif : hausse composée supplémentaire de ${parameters.rentIncrease.annualExtraRate * 100}% dès t=${parameters.rentIncrease.startYear}, en plus de l'inflation."
            else "S037 désactivé : loyer indexé uniquement sur l'inflation.",
            "Flux de l'année t : état t vers t+1. Les versements investis sont effectués en fin d'année. Cash négatif signalé, sans financement automatique.",
        ) + maintenance(parameters)
    }

    fun maintenance(parameters: SimulationParameters): List<String> {
        val bill = parameters.maintenance
        val effect = SimulationComparisons.maintenanceEffect(parameters)
        return listOf(
            "S040 — Unexpected maintenance : Normal upkeep → Major repair bill.",
            if (bill.enabled) "Réparation ponctuelle de ${bill.nominalAmount} CHF nominaux durant l'année t=${bill.paymentYear}."
            else "S040 désactivé : aucun coût de réparation exceptionnel.",
            "Convention explicite : cette réparation est à la charge du personnage. Cela ne signifie pas que toute grosse réparation incombe à un locataire.",
            "Les frais habituels restent dans les dépenses normales. Le montant est nominal à l'année de paiement : aucune inflation supplémentaire.",
            "Aucun emprunt automatique. Un cash négatif signale un besoin de financement sans le financer.",
            "Écart de patrimoine final en retirant uniquement S040 : ${effect.finalNetWorthDifference} CHF. Les trajectoires sont recalculées avec leurs interactions ; les effets individuels ne s'additionnent pas.",
        )
    }
}
