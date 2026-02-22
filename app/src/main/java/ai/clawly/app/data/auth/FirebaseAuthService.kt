package ai.clawly.app.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirebaseAuthService"

sealed class FirebaseAuthState {
    object Loading : FirebaseAuthState()
    object NotAuthenticated : FirebaseAuthState()
    data class Authenticated(
        val uid: String,
        val email: String?,
        val displayName: String?,
        val photoUrl: String?
    ) : FirebaseAuthState()
}

@Singleton
class FirebaseAuthService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SERVER_CLIENT_ID = "187834608967-fgu627vsccqcg93lq0lpju8l9ha77g77.apps.googleusercontent.com"
    }

    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val authState: StateFlow<FirebaseAuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val state = if (user != null) {
                FirebaseAuthState.Authenticated(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString()
                )
            } else {
                FirebaseAuthState.NotAuthenticated
            }
            trySend(state)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, FirebaseAuthState.Loading)

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    val firebaseUid: String?
        get() = auth.currentUser?.uid

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(SERVER_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()

            val user = authResult.user
            if (user != null) {
                Log.d(TAG, "Sign-in successful: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Sign-in succeeded but user is null"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun getIdToken(forceRefresh: Boolean = false): Result<String> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("Not signed in"))
            val tokenResult = user.getIdToken(forceRefresh).await()
            val token = tokenResult.token
            if (token != null) {
                Result.success(token)
            } else {
                Result.failure(Exception("Failed to get ID token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ID token", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
        Log.d(TAG, "Signed out")
    }
}
