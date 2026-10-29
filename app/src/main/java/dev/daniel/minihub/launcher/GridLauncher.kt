package dev.daniel.minihub.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.Launcher
import dev.daniel.minihub.core.MiniApp

object GridLauncher : Launcher {
    override val id = "grid"
    override val name = "Cards"
    override val icon = "▦"

    @Composable
    override fun Content(apps: List<MiniApp>, onOpen: (MiniApp) -> Unit, modifier: Modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(apps, key = { it.id }) { app ->
                Card(
                    onClick = { onOpen(app) },
                    modifier = Modifier.aspectRatio(1f),
                    colors = CardDefaults.cardColors(containerColor = app.accent.copy(alpha = 0.18f)),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(app.icon, fontSize = 36.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(app.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}
