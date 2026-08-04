package com.monumentogram.dora.bootstrap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monumentogram.dora.bootstrap.R
import com.monumentogram.dora.bootstrap.ui.theme.DoraDimensions
import com.monumentogram.dora.model.BootstrapDestination

@Composable
internal fun DestinationPlaceholder(
    destination: BootstrapDestination,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier.widthIn(max = DoraDimensions.readingColumnMaximum)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DoraDimensions.space6)
                    .padding(
                        top = DoraDimensions.space8,
                        bottom = DoraDimensions.space8,
                    ),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space6),
        ) {
            StageBadge()
            Text(
                text = stringResource(destination.labelResource()),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            PlaceholderCard(destination = destination)
        }
    }
}

@Composable
private fun StageBadge() {
    Surface(
        shape = RoundedCornerShape(DoraDimensions.radiusFull),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.stage00_label),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.padding(
                    horizontal = DoraDimensions.space3,
                    vertical = DoraDimensions.space2,
                ),
        )
    }
}

@Composable
private fun PlaceholderCard(destination: BootstrapDestination) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(DoraDimensions.space6),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space4),
        ) {
            Icon(
                painter = painterResource(destination.iconResource()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(DoraDimensions.space8),
            )
            Text(
                text = stringResource(R.string.bootstrap_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(destination.placeholderResource()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.bootstrap_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
