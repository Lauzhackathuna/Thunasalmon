package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tuna.finlife.game.GameState
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.Persona
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.camille_name
import finlife.shared.generated.resources.camille_role
import finlife.shared.generated.resources.camille_story
import finlife.shared.generated.resources.choose_persona
import finlife.shared.generated.resources.first_year_abroad
import finlife.shared.generated.resources.maya_name
import finlife.shared.generated.resources.maya_role
import finlife.shared.generated.resources.maya_story
import finlife.shared.generated.resources.opening_cash
import finlife.shared.generated.resources.room_title
import finlife.shared.generated.resources.samir_name
import finlife.shared.generated.resources.samir_role
import finlife.shared.generated.resources.samir_story
import finlife.shared.generated.resources.simulation_note
import finlife.shared.generated.resources.synthetic_data
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PersonaSelectionScreen(
    openingSnapshots: Map<Persona, FinancialSnapshot>,
    onSelect: (Persona) -> Unit,
) {
    GameScreen {
        ScreenHeading(stringResource(Res.string.first_year_abroad))
        Text(stringResource(Res.string.choose_persona), style = MaterialTheme.typography.titleMedium)
        for ((persona, opening) in openingSnapshots) {
            val copy = personaCopy(persona)
            OutlinedCard(
                onClick = { onSelect(persona) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(FinLifeSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                ) {
                    Text(stringResource(copy.name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(copy.role), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(copy.story), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(Res.string.opening_cash) + " · " + displayMoney(opening.cash),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoomScreen(state: GameState.Playing) {
    GameScreen {
        ScreenHeading(stringResource(Res.string.room_title))
        Text(stringResource(personaCopy(state.persona).name), style = MaterialTheme.typography.titleLarge)
        KeyFigureCard(
            label = stringResource(Res.string.opening_cash),
            value = displayMoney(state.snapshot.cash),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GameScreen(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeContentPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .widthIn(max = FinLifeContentWidth)
                        .fillMaxWidth()
                        .padding(FinLifeSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                ) {
                    content()
                }
            }
            Column(
                modifier = Modifier.widthIn(max = FinLifeContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = FinLifeSpacing.large, vertical = FinLifeSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                HorizontalDivider()
                Text(stringResource(Res.string.synthetic_data), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(Res.string.simulation_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() },
    )
}

private data class PersonaCopy(val name: StringResource, val role: StringResource, val story: StringResource)

private fun personaCopy(persona: Persona): PersonaCopy = when (persona) {
    Persona.MastersStudent -> PersonaCopy(Res.string.maya_name, Res.string.maya_role, Res.string.maya_story)
    Persona.NewlyArrivedWorker -> PersonaCopy(Res.string.samir_name, Res.string.samir_role, Res.string.samir_story)
    Persona.Apprentice -> PersonaCopy(Res.string.camille_name, Res.string.camille_role, Res.string.camille_story)
}
