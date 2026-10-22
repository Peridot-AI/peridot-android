package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.data.ApiException
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * "Continue with Google" through Credential Manager. The token is requested for the website's
 * OAuth client ([serverClientId]), so the server accepts it like a sign-in on the website; Google
 * only issues it to this app when the Cloud project has an Android client for its package and
 * signing certificate (see the README).
 *
 * Returns the Google ID token, or null when the user closed the account picker.
 */
suspend fun googleIdToken(context: Context, serverClientId: String): String? {
    val activity = context.findActivity() ?: context
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()
    val credential = try {
        CredentialManager.create(activity).getCredential(activity, request).credential
    } catch (_: GetCredentialCancellationException) {
        return null
    } catch (e: GetCredentialException) {
        throw ApiException(context.getString(R.string.google_unavailable), 0).apply { initCause(e) }
    }
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw ApiException(context.getString(R.string.google_unavailable), 0)
    }
    return try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (e: GoogleIdTokenParsingException) {
        throw ApiException(context.getString(R.string.google_unavailable), 0).apply { initCause(e) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
