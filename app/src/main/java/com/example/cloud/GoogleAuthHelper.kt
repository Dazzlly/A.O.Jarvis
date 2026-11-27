package com.example.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

/**
 * Safely obtains FirebaseAuth without throwing IllegalStateException when
 * FirebaseApp is uninitialized (e.g., builds compiled without google-services.json).
 */
fun getFirebaseAuthOrNull(context: Context): FirebaseAuth? {
    return try {
        val appContext = context.applicationContext
        if (FirebaseRuntimeConfig.ensureFirebaseInitialized(appContext)) {
            Firebase.auth
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w(AUTH_TAG, "FirebaseAuth unavailable: ${t.javaClass.simpleName}: ${t.message}")
        null
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun resolveDefaultWebClientId(context: Context): String? {
    return FirebaseRuntimeConfig.resolveDefaultWebClientId(context)
}

/**
 * Silent auto sign-in using GetGoogleIdOption (one credential option per request).
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val auth = getFirebaseAuthOrNull(context)
    if (auth == null || credentialManager == null) {
        onUnauthenticated()
        return
    }
    if (auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Throwable) {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Google Sign-In using GetSignInWithGoogleOption (one credential option per request).
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val auth = getFirebaseAuthOrNull(context)
    if (auth == null) {
        onAuthError("Firebase indisponível nesta compilação. Use o modo local ou configure google-services.json no build.")
        return
    }
    if (credentialManager == null) {
        onAuthError("Google Credential Manager indisponível neste dispositivo.")
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onAuthError("Configuração Google Sign-In ausente: default_web_client_id não encontrado.")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Activity indisponível para iniciar login com Google.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Tipo de credencial inesperado recebido do Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Falha ao entrar com Google.")
        }
    }
}

/**
 * Safely clears Credential Manager state and signs out from Firebase Auth inside NonCancellable.
 */
fun signOutWithCredentialClear(
    context: Context,
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Throwable) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { getFirebaseAuthOrNull(context)?.signOut() }
                onSignOutComplete()
            }
        }
    }
}
