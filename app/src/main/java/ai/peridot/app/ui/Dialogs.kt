package ai.peridot.app.ui

import ai.peridot.app.BuildConfig
import ai.peridot.app.R
import ai.peridot.app.data.Conversation
import ai.peridot.app.ui.theme.Peridot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sign in with Google or with email and password. New email accounts are made on the website
 * (it also has the legal pages); Google sign-in works for any Google account, like on the website.
 */
@Composable
fun SignInDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !auth.busy
    val submit = { if (canSubmit) vm.signIn(email, password, onDone = onDismiss) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Peridot.Mint,
        unfocusedBorderColor = Peridot.Border,
        focusedLabelColor = Peridot.Mint,
        cursorColor = Peridot.Mint,
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Peridot.Background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                IconButton(onClick = onDismiss, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(24.dp))
                        PeridotLogo(44.dp)
                        Spacer(Modifier.height(18.dp))
                        Text(
                            stringResource(R.string.sign_in_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(28.dp))
                        OutlinedButton(
                            onClick = {
                                vm.clearAuthError()
                                vm.signInWithGoogle({ googleIdToken(context, it) }, onDone = onDismiss)
                            },
                            enabled = !auth.busy,
                            border = BorderStroke(1.dp, Peridot.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Peridot.Text),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.continue_with_google))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = Peridot.Border)
                            Text(
                                stringResource(R.string.or),
                                color = Peridot.Muted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            HorizontalDivider(Modifier.weight(1f), color = Peridot.Border)
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; vm.clearAuthError() },
                            label = { Text(stringResource(R.string.email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; vm.clearAuthError() },
                            label = { Text(stringResource(R.string.password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentType = ContentType.Password },
                        )
                        auth.error?.let {
                            Text(
                                it,
                                color = Peridot.Error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { submit() },
                            enabled = canSubmit,
                            colors = ButtonDefaults.buttonColors(containerColor = Peridot.Mint, contentColor = Peridot.OnMint),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            if (auth.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Peridot.OnMint, strokeWidth = 2.dp)
                            else Text(stringResource(R.string.sign_in))
                        }
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = { openUrl(context, "${BuildConfig.WEB_URL}/signup") }) {
                            Text(stringResource(R.string.create_account_on_web), color = Peridot.Mint, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(conversation: Conversation, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(conversation.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Peridot.Surface,
        title = { Text(stringResource(R.string.rename_chat)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peridot.Mint, cursorColor = Peridot.Mint),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(title); onDismiss() }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.save), color = Peridot.Mint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Peridot.Muted) }
        },
    )
}

@Composable
fun DeleteDialog(conversation: Conversation, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Peridot.Surface,
        title = { Text(stringResource(R.string.delete_chat)) },
        text = { Text(stringResource(R.string.delete_chat_text, conversation.title)) },
        confirmButton = {
            TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.delete), color = Peridot.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Peridot.Muted) }
        },
    )
}
