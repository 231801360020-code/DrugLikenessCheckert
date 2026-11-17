package com.example.druglikenesschecker.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: Uri?,
    val isGuest: Boolean = false
)

object AuthManager {

    private var guestUser: UserProfile? = null

    fun getGoogleSignInClient(context: Context, webClientId: String = ""): GoogleSignInClient {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        if (webClientId.isNotBlank()) {
            gsoBuilder.requestIdToken(webClientId)
        }

        return GoogleSignIn.getClient(context, gsoBuilder.build())
    }

    fun getCurrentUser(): UserProfile? {
        val fbUser: FirebaseUser? = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }

        if (fbUser != null) {
            return UserProfile(
                uid = fbUser.uid,
                displayName = fbUser.displayName ?: "User",
                email = fbUser.email,
                photoUrl = fbUser.photoUrl
            )
        }

        return guestUser
    }

    fun setGuestUser(name: String = "Guest Scientist") {
        guestUser = UserProfile(
            uid = "guest_12345",
            displayName = name,
            email = "guest@druglikeness.app",
            photoUrl = null,
            isGuest = true
        )
    }

    fun handleSignInResult(
        completedTask: Task<GoogleSignInAccount>,
        onSuccess: (UserProfile) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user
                        if (user != null) {
                            val profile = UserProfile(
                                uid = user.uid,
                                displayName = user.displayName ?: account.displayName ?: "Google User",
                                email = user.email ?: account.email,
                                photoUrl = user.photoUrl ?: account.photoUrl
                            )
                            onSuccess(profile)
                        } else {
                            val fallback = UserProfile(
                                uid = account.id ?: "google_user",
                                displayName = account.displayName ?: "Google User",
                                email = account.email,
                                photoUrl = account.photoUrl
                            )
                            onSuccess(fallback)
                        }
                    }
                    .addOnFailureListener {
                        // Fallback to Google Account info if Firebase credential auth requires configuration
                        val fallbackProfile = UserProfile(
                            uid = account.id ?: "google_user",
                            displayName = account.displayName ?: "Google User",
                            email = account.email,
                            photoUrl = account.photoUrl
                        )
                        onSuccess(fallbackProfile)
                    }
            } else {
                val profile = UserProfile(
                    uid = account.id ?: "google_user",
                    displayName = account.displayName ?: "Google User",
                    email = account.email,
                    photoUrl = account.photoUrl
                )
                onSuccess(profile)
            }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}

        try {
            getGoogleSignInClient(context).signOut().addOnCompleteListener {
                guestUser = null
                onComplete()
            }
        } catch (_: Exception) {
            guestUser = null
            onComplete()
        }
    }
}
