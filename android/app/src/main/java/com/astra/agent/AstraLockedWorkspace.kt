package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** No saved private chat, note, task or document content is composed here. */
@Composable
internal fun AstraLockedWorkspace(
    onRequestUnlock: () -> Unit,
    onOpenDeviceSecurity: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(AstraAurora.background, AstraAurora.night, AstraAurora.background)
            )
        ).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AstraOrb(diameter = 116.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                "Astra is locked",
                color = AstraAurora.ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Your private workspace stays hidden until Android verifies you.",
                color = AstraAurora.muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(23.dp))
            Button(
                onClick = onRequestUnlock,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .semantics { contentDescription = "Unlock with fingerprint or device PIN" }
            ) {
                Text("Unlock with fingerprint or PIN")
            }
            Spacer(Modifier.height(9.dp))
            TextButton(onClick = onOpenDeviceSecurity) {
                Text("Set up Android screen lock")
            }
        }
    }
}
