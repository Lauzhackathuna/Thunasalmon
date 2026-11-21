package com.tuna.finlife.game

import com.tuna.finlife.simulation.SimulationSession

/**
 * The what-ifs chosen for the current month, kept separate from [base] (the session before them)
 * so they can still be switched off. Discard it once the month is advanced.
 */
internal data class WhatIfDraft(val base: SimulationSession, val selected: Set<WhatIf> = emptySet()) {
    val session: SimulationSession get() = AdventureRun.withWhatIfs(base, selected)

    fun toggle(whatIf: WhatIf, on: Boolean): WhatIfDraft {
        if (on && AdventureRun.whatIfStates(base)[whatIf] != WhatIfState.Available) return this
        return copy(selected = if (on) selected + whatIf else selected - whatIf)
    }

    /** Switch position shown to the player: on if chosen now or already in effect from an earlier month. */
    fun isOn(whatIf: WhatIf): Boolean = whatIf in selected || AdventureRun.whatIfStates(base)[whatIf] == WhatIfState.Active

    /** Only what-ifs that could be applied this month can be switched; earlier ones are locked. */
    fun canSwitch(whatIf: WhatIf): Boolean = AdventureRun.whatIfStates(base)[whatIf] == WhatIfState.Available
}
