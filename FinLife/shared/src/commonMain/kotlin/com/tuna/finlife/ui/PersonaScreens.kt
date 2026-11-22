package com.tuna.finlife.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.tuna.finlife.i18n.displayMoney
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
import finlife.shared.generated.resources.samir_name
import finlife.shared.generated.resources.samir_role
import finlife.shared.generated.resources.samir_story
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PersonaSelectionScreen(
    openingCash: Double,
    onOpenSettings: () -> Unit,
    onSelect: () -> Unit,
) {
    GameScreen(onOpenSettings = onOpenSettings) {
        ScreenHeading(stringResource(Res.string.first_year_abroad))
        Text(stringResource(Res.string.choose_persona), style = MaterialTheme.typography.titleMedium)
        val copy = personaCopy(Persona.MastersStudent)
        OutlinedCard(
            onClick = onSelect,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(FinLifeSpacing.large),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
            ) {
                // A restrained signature stripe keeps the character screen recognizable.
                Box(
                    Modifier.width(4.dp).height(104.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(copy.role), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(copy.story), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(Res.string.opening_cash) + " · " + displayMoney(openingCash),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

internal data class PersonaCopy(val name: StringResource, val role: StringResource, val story: StringResource)

internal fun personaCopy(persona: Persona): PersonaCopy = when (persona) {
    Persona.MastersStudent -> PersonaCopy(Res.string.maya_name, Res.string.maya_role, Res.string.maya_story)
    Persona.NewlyArrivedWorker -> PersonaCopy(Res.string.samir_name, Res.string.samir_role, Res.string.samir_story)
    Persona.Apprentice -> PersonaCopy(Res.string.camille_name, Res.string.camille_role, Res.string.camille_story)
}
