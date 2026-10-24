package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.data.Conversation
import ai.peridot.app.ui.theme.Peridot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var input by rememberSaveable { mutableStateOf("") }
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showShare by rememberSaveable { mutableStateOf(false) }
    var showUpgrade by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Back from the payment page in the browser: see whether the plan is active yet
    LifecycleResumeEffect(state.awaitingPlan) {
        if (state.awaitingPlan) vm.refreshAccount()
        onPauseOrDispose {}
    }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.noticeShown()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            Sidebar(
                state = state,
                onNewChat = { vm.newChat(); scope.launch { drawer.close() } },
                onOpen = { vm.openConversation(it); scope.launch { drawer.close() } },
                onRename = { renaming = it },
                onDelete = { deleting = it },
                onSignIn = { showSignIn = true; scope.launch { drawer.close() } },
                onAccount = { showAccount = true; scope.launch { drawer.close() } },
            )
        },
    ) {
        Scaffold(
            containerColor = Peridot.Background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Peridot.Background),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (state.signedIn) vm.loadConversations()
                            scope.launch { drawer.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.open_menu))
                        }
                    },
                    title = {
                        TextButton(onClick = { showModels = true }) {
                            Text(
                                state.currentModel?.name ?: stringResource(R.string.app_name),
                                color = Peridot.Text,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.choose_model), tint = Peridot.Muted)
                        }
                    },
                    actions = {
                        if (state.signedIn && state.conversationId != null && state.messages.isNotEmpty()) {
                            IconButton(onClick = { showShare = true }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_chat))
                            }
                        }
                        IconButton(onClick = vm::newChat, enabled = state.messages.isNotEmpty() || state.conversationId != null) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loadingConversation -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Peridot.Mint)
                        state.messages.isEmpty() -> EmptyState(Modifier.align(Alignment.Center))
                        else -> Messages(state.messages, onRetry = vm::retry)
                    }
                }
                Composer(
                    value = input,
                    onValueChange = { input = it },
                    streaming = state.streaming,
                    onSend = { if (vm.send(input)) input = "" },
                    onStop = vm::stop,
                )
            }
        }
    }

    if (showSignIn) {
        SignInDialog(vm, onDismiss = { showSignIn = false; vm.clearAuthError() })
    }
    if (showModels) {
        ModelSheet(
            state,
            onSelect = vm::selectModel,
            onEffort = vm::selectEffort,
            onUpgrade = { showModels = false; if (state.signedIn) showUpgrade = true else showSignIn = true },
            onDismiss = { showModels = false },
        )
    }
    if (showAccount) {
        AccountSheet(
            state,
            onRefresh = vm::refreshAccount,
            onSignOut = { showAccount = false; vm.signOut() },
            onUpgrade = { showAccount = false; showUpgrade = true },
            onLoadInvites = vm::loadReferrals,
            onDismiss = { showAccount = false },
        )
    }
    if (showShare) {
        ShareChatDialog(
            state,
            onLoad = vm::loadShare,
            onShare = { vm.share { link -> shareText(context, link) } },
            onStop = vm::stopSharing,
            onDismiss = { showShare = false },
        )
    }
    if (showUpgrade) {
        UpgradeSheet(
            state,
            onLoad = vm::loadPlans,
            onCheckout = { plan -> vm.checkout(plan) { url -> openUrl(context, url) } },
            onCheckAgain = vm::refreshAccount,
            onDismiss = { showUpgrade = false },
        )
    }
    renaming?.let { conversation ->
        RenameDialog(conversation, onRename = { vm.renameConversation(conversation, it) }, onDismiss = { renaming = null })
    }
    deleting?.let { conversation ->
        DeleteDialog(conversation, onDelete = { vm.deleteConversation(conversation) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PeridotLogo(44.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium, color = Peridot.Text)
    }
}

@Composable
private fun Messages(messages: List<ChatMessage>, onRetry: () -> Unit) {
    val list = rememberLazyListState()
    // Follow the answer while it streams, until the user scrolls up to read; back at the bottom, follow again
    var follow by remember { mutableStateOf(true) }
    val userScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) follow = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.canScrollForward }.collect { if (!it) follow = true }
    }
    val last = messages.last()
    LaunchedEffect(messages.size) {
        if (last.role == Role.Model && last.streaming) follow = true
        if (follow) list.scrollToItem(messages.size)
    }
    LaunchedEffect(last.text.length, last.error) {
        if (follow) list.scrollToItem(messages.size)
    }

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().nestedScroll(userScroll),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            if (message.role == Role.User) UserBubble(message.text)
            else ModelMessage(message, isLast = message.id == last.id, onRetry = onRetry)
        }
        // Scroll target that is always below the last message
        item(key = "end") { Spacer(Modifier.height(1.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(text: String) {
    val copyText = rememberCopy()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = Peridot.SurfaceHigh,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(onClick = {}, onLongClick = { copyText(text) }),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun ModelMessage(message: ChatMessage, isLast: Boolean, onRetry: () -> Unit) {
    val copyText = rememberCopy()
    Column(Modifier.fillMaxWidth()) {
        if (message.text.isEmpty() && message.streaming) {
            PeridotLogo(22.dp, thinking = true, modifier = Modifier.padding(vertical = 4.dp))
        } else if (message.text.isNotEmpty()) {
            SelectionContainer { MarkdownText(message.text) }
        }
        val error = message.error
        if (error != null) {
            Text(error, color = Peridot.Error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            if (isLast) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(R.string.retry), color = Peridot.Mint)
                }
            }
        } else if (!message.streaming && message.text.isNotEmpty()) {
            TextButton(
                onClick = { copyText(message.text) },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(R.string.copy), color = Peridot.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = Peridot.Surface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Peridot.Border),
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Peridot.Text),
                cursorBrush = SolidColor(Peridot.Mint),
                maxLines = 6,
                modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty()) {
                            Text(stringResource(R.string.message_hint), style = MaterialTheme.typography.bodyLarge, color = Peridot.Muted)
                        }
                        field()
                    }
                },
            )
            val canSend = value.isNotBlank()
            IconButton(
                onClick = if (streaming) onStop else onSend,
                enabled = streaming || canSend,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(40.dp)
                    .background(if (streaming || canSend) Peridot.Mint else Peridot.SurfaceHigh, CircleShape),
            ) {
                if (streaming) {
                    val stop = stringResource(R.string.stop)
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(Peridot.OnMint, RoundedCornerShape(2.dp))
                            .semantics { contentDescription = stop },
                    )
                } else {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.send),
                        tint = if (canSend) Peridot.OnMint else Peridot.Muted,
                    )
                }
            }
        }
    }
}
