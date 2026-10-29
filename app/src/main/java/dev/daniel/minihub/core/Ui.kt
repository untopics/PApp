package dev.daniel.minihub.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun MiniTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

fun stepLabel(step: Int): String = if (step > 0) "+$step" else "−${-step}"

/** A row of equally wide step buttons. Positive = filled, negative = tonal. */
@Composable
fun StepRow(steps: List<Int>, onStep: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in steps) {
            val m = Modifier.weight(1f).height(height)
            if (s > 0) {
                Button(onClick = { onStep(s) }, modifier = m, contentPadding = PaddingValues(0.dp)) {
                    Text(stepLabel(s), fontSize = 18.sp)
                }
            } else {
                FilledTonalButton(onClick = { onStep(s) }, modifier = m, contentPadding = PaddingValues(0.dp)) {
                    Text(stepLabel(s), fontSize = 18.sp)
                }
            }
        }
    }
}

/** Plain segmented choice without animations. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (o in options) {
            val m = Modifier.weight(1f).height(40.dp)
            if (o == selected) {
                Button(onClick = { onSelect(o) }, modifier = m, contentPadding = PaddingValues(0.dp)) { Text(label(o)) }
            } else {
                OutlinedButton(onClick = { onSelect(o) }, modifier = m, contentPadding = PaddingValues(0.dp)) { Text(label(o)) }
            }
        }
    }
}

/** Destructive button without a dialog: first tap arms it for 3 s, second tap confirms. */
@Composable
fun ArmedButton(label: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    if (armed) {
        Button(
            onClick = { armed = false; onConfirm() },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Tap again") }
    } else {
        OutlinedButton(onClick = { armed = true }, modifier = modifier) { Text(label) }
    }
}
