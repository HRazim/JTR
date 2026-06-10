package com.jtr.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.typeLabelResOrNull

/**
 * Bandeau « Événements à venir » de l'Accueil : carrousel horizontal des
 * anniversaires / dates clés des 7 prochains jours, trié chronologiquement.
 * Se masque ENTIÈREMENT quand il n'y a aucun événement (zéro encombrement).
 */
@Composable
fun UpcomingEventsBanner(
    events: List<UpcomingEvent>,
    onEventClick: (UpcomingEvent) -> Unit
) {
    if (events.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Cake,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = stringResource(R.string.events_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = events,
                key = { "${it.person.id}_${it.typeKey}_${it.daysUntil}" }
            ) { event ->
                UpcomingEventCard(event = event, onClick = { onEventClick(event) })
            }
        }
    }
}

/** Mini-carte compacte et colorée : avatar + nom + type d'événement + J-X. */
@Composable
private fun UpcomingEventCard(
    event: UpcomingEvent,
    onClick: () -> Unit
) {
    // Teinte selon l'imminence : aujourd'hui → primaire, demain/après → secondaire,
    // plus loin → tertiaire. Le compte à rebours reste l'information dominante.
    val container = when {
        event.daysUntil == 0 -> MaterialTheme.colorScheme.primaryContainer
        event.daysUntil <= 2 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer = when {
        event.daysUntil == 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        event.daysUntil <= 2 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier
            .widthIn(min = 168.dp, max = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatar(person = event.person, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = event.person.fullName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Type localisé si standard (Anniversaire, Fête…), brut si personnalisé.
                val labelRes = typeLabelResOrNull(FieldTypes.DATE, event.typeKey)
                Text(
                    text = labelRes?.let { stringResource(it) } ?: event.typeKey,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            // Pastille compte à rebours « J-X » (ou « Aujourd'hui »).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(onContainer.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (event.daysUntil == 0) stringResource(R.string.event_today)
                    else stringResource(R.string.event_countdown, event.daysUntil),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                    maxLines = 1
                )
            }
        }
    }
}
