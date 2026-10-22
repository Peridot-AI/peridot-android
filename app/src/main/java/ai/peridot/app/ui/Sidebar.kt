package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.data.Conversation
import ai.peridot.app.ui.theme.Peridot
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Sidebar(
    state: ChatState,
    onNewChat: () -> Unit,
    onOpen: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = Peridot.Surface, drawerContentColor = Peridot.Text) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                PeridotLogo(22.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onNewChat)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Peridot.Mint)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.new_chat), fontSize = 15.sp)
            }

            Text(
                stringResource(R.string.chats),
                color = Peridot.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !state.signedIn -> Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(stringResource(R.string.history_signed_out), color = Peridot.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.padding(top = 14.dp))
                        Button(
                            onClick = onSignIn,
                            colors = ButtonDefaults.buttonColors(containerColor = Peridot.Mint, contentColor = Peridot.OnMint),
                        ) { Text(stringResource(R.string.sign_in)) }
                    }
                    state.conversations.isEmpty() -> Text(
                        stringResource(R.string.no_chats),
                        color = Peridot.Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.conversations, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation,
                                selected = conversation.id == state.conversationId,
                                onOpen = { onOpen(conversation) },
                                onRename = { onRename(conversation) },
                                onDelete = { onDelete(conversation) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Peridot.Border)
            val user = state.user
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = if (user != null) onAccount else onSignIn)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (user != null) {
                    val name = user.name?.takeIf { it.isNotBlank() } ?: user.email
                    Avatar(name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            planName(state.limits?.plan ?: user.plan, state.limits?.unlimited == true),
                            color = Peridot.Muted,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    Text(stringResource(R.string.sign_in), color = Peridot.Mint, fontSize = 15.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) Peridot.SurfaceHigh else Peridot.Surface)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Text(
                conversation.title.ifBlank { stringResource(R.string.untitled) },
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val time = ago(conversation.updatedAt)
            if (time.isNotEmpty()) Text(time, color = Peridot.Muted, fontSize = 12.sp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = Peridot.Error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}
