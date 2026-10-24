package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.data.CheckoutPlan
import ai.peridot.app.data.euros
import ai.peridot.app.data.inviteLink
import ai.peridot.app.ui.theme.Peridot
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hands a link to the system share sheet (messages, mail, copy…). */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** Share the open chat: a public, read-only copy at WEB_URL/share/<id>. Sharing again updates it. */
@Composable
fun ShareChatDialog(state: ChatState, onLoad: () -> Unit, onShare: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }
    val shared = state.share != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Peridot.Surface,
        title = { Text(stringResource(R.string.share_chat)) },
        text = { Text(stringResource(R.string.share_explain), color = Peridot.Muted) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onShare() }) {
                Text(stringResource(if (shared) R.string.update_link else R.string.share_link), color = Peridot.Mint)
            }
        },
        dismissButton = {
            if (shared) {
                TextButton(onClick = { onDismiss(); onStop() }) { Text(stringResource(R.string.stop_sharing), color = Peridot.Error) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Peridot.Muted) }
            }
        },
    )
}

/** In the account sheet: your invite link and how many friends joined. */
@Composable
fun InviteSection(state: ChatState, onLoad: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onLoad() }
    val r = state.referrals ?: return
    val link = inviteLink(r.code)
    Text(stringResource(R.string.invite_friends), color = Peridot.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
    Text(
        stringResource(R.string.invite_explain, "${r.friendPercentOff}%", euros(r.referrerCreditCents)),
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(8.dp))
    Text(link, color = Peridot.Mint, fontSize = 14.sp)
    Text(
        stringResource(R.string.invite_stats, r.invited, r.paid, euros(r.earnedCents)),
        color = Peridot.Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    OutlinedButton(
        onClick = { shareText(context, link) },
        border = BorderStroke(1.dp, Peridot.Border),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text(stringResource(R.string.share_link), color = Peridot.Text) }
}

/**
 * Pick a plan and pay on the website's checkout in the browser, signed in for this account (the terms are
 * accepted there). Back in the app the account is reloaded (on resume) until the plan is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    state: ChatState,
    onLoad: () -> Unit,
    onCheckout: (String) -> Unit,
    onCheckAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    var selected by rememberSaveable { mutableStateOf("plus") }
    val paid = (state.user?.plan ?: state.limits?.plan ?: "free") != "free"

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Peridot.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.upgrade_plan), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            val config = state.plans
            when {
                paid && !state.awaitingPlan -> Text(stringResource(R.string.already_paid), color = Peridot.Muted)
                state.awaitingPlan -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Peridot.Mint, strokeWidth = 2.dp, modifier = Modifier.padding(end = 12.dp).height(20.dp).width(20.dp))
                        Text(stringResource(R.string.finish_payment), color = Peridot.Text)
                    }
                    OutlinedButton(
                        onClick = onCheckAgain,
                        border = BorderStroke(1.dp, Peridot.Border),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) { Text(stringResource(R.string.check_again), color = Peridot.Text) }
                }
                config == null -> CircularProgressIndicator(color = Peridot.Mint)
                else -> {
                    config.plans.forEach { plan ->
                        PlanCard(
                            plan,
                            selected = plan.id == selected,
                            trialDays = state.offer?.trial?.takeIf { it.plan == plan.id }?.days,
                            onClick = { selected = plan.id },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    state.offer?.friendPercentOff?.let {
                        Text(
                            stringResource(R.string.friend_discount, it),
                            color = Peridot.Mint,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Peridot.Mint.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    val trial = state.offer?.trial?.plan == selected
                    Button(
                        onClick = { onCheckout(selected) },
                        enabled = config.enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = Peridot.Mint, contentColor = Peridot.OnMint),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    !config.enabled -> R.string.payments_off
                                    trial -> R.string.start_free_trial
                                    else -> R.string.continue_to_payment
                                }
                            ),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        stringResource(R.string.payment_in_browser),
                        color = Peridot.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: CheckoutPlan, selected: Boolean, trialDays: Int?, onClick: () -> Unit) {
    val blurb = when (plan.id) {
        "plus" -> R.string.plan_plus_blurb
        "extended" -> R.string.plan_extended_blurb
        else -> R.string.plan_extended10_blurb
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Peridot.Mint else Peridot.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name.removePrefix("Peridot "), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.per_month, euros(plan.amountCents)), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (trialDays != null) {
            Text(
                stringResource(R.string.free_trial_days, trialDays),
                color = Peridot.OnMint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Peridot.Mint, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(stringResource(blurb), color = Peridot.Muted, fontSize = 13.sp)
        Text(stringResource(R.string.messages_per_day, plan.dailyMessages), fontSize = 13.sp)
    }
}
