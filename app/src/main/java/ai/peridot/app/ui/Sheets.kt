package ai.peridot.app.ui

import ai.peridot.app.BuildConfig
import ai.peridot.app.R
import ai.peridot.app.data.Effort
import ai.peridot.app.data.Model
import ai.peridot.app.ui.theme.Peridot
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    state: ChatState,
    onSelect: (Model) -> Unit,
    onEffort: (Effort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Peridot.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp).navigationBarsPadding()) {
            SheetTitle(stringResource(R.string.model))
            if (state.models.isEmpty()) {
                Text(
                    stringResource(R.string.models_unavailable),
                    color = Peridot.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            state.models.forEach { model ->
                ModelRow(model, selected = model.id == state.model, onClick = { onSelect(model); onDismiss() })
            }

            SheetTitle(stringResource(R.string.effort), Modifier.padding(top = 12.dp))
            val efforts = Effort.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                efforts.forEachIndexed { i, effort ->
                    SegmentedButton(
                        selected = effort == state.effort,
                        onClick = { onEffort(effort) },
                        shape = SegmentedButtonDefaults.itemShape(i, efforts.size),
                    ) {
                        Text(
                            stringResource(
                                when (effort) {
                                    Effort.Low -> R.string.effort_low
                                    Effort.Medium -> R.string.effort_medium
                                    Effort.High -> R.string.effort_high
                                }
                            )
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.effort_hint),
                color = Peridot.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ModelRow(model: Model, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !model.locked, onClick = onClick)
            .background(if (selected) Peridot.SurfaceHigh else Peridot.Surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.name,
                    fontSize = 16.sp,
                    color = if (model.locked) Peridot.Muted else Peridot.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                model.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        badge,
                        fontSize = 11.sp,
                        color = Peridot.Mint,
                        modifier = Modifier
                            .background(Peridot.Mint.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val detail = if (model.locked) stringResource(R.string.not_in_plan) else model.desc
            if (detail.isNotBlank()) Text(detail, color = Peridot.Muted, fontSize = 13.sp)
        }
        when {
            model.locked -> Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.not_in_plan), tint = Peridot.Muted)
            selected -> Icon(Icons.Default.Check, contentDescription = null, tint = Peridot.Mint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    state: ChatState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Peridot.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            val user = state.user
            if (user != null) {
                val name = user.name?.takeIf { it.isNotBlank() } ?: user.email
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.email.isNotBlank() && user.email != name) {
                            Text(user.email, color = Peridot.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            val limits = state.limits
            Spacer(Modifier.height(22.dp))
            Label(stringResource(R.string.plan))
            Text(planName(limits?.plan ?: user?.plan, limits?.unlimited == true), fontSize = 16.sp)

            if (limits != null && !limits.unlimited) {
                Spacer(Modifier.height(18.dp))
                Label(stringResource(R.string.messages_today))
                Text(stringResource(R.string.used_of, limits.dailyUsed, limits.dailyLimit), fontSize = 16.sp)
                Meter(if (limits.dailyLimit > 0) limits.dailyUsed.toFloat() / limits.dailyLimit else 0f)

                Spacer(Modifier.height(18.dp))
                Label(stringResource(R.string.monthly_usage))
                Text(stringResource(R.string.percent_used, limits.monthlyPercent), fontSize = 16.sp)
                Meter(limits.monthlyPercent / 100f)
            }

            Spacer(Modifier.height(26.dp))
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sign_out), color = Peridot.Text)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Peridot.Border)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                TextButton(onClick = { openUrl(context, "${BuildConfig.WEB_URL}/privacy") }) {
                    Text(stringResource(R.string.privacy), color = Peridot.Muted, fontSize = 13.sp)
                }
                TextButton(onClick = { openUrl(context, "${BuildConfig.WEB_URL}/terms") }) {
                    Text(stringResource(R.string.terms), color = Peridot.Muted, fontSize = 13.sp)
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = Peridot.Surface,
            title = { Text(stringResource(R.string.sign_out_title)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) {
                    Text(stringResource(R.string.sign_out), color = Peridot.Mint)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel), color = Peridot.Muted) }
            },
        )
    }
}

@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, color = Peridot.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun Meter(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = Peridot.Mint,
        trackColor = Peridot.SurfaceHigh,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
    )
}
