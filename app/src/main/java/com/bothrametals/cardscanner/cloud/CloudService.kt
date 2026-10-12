package com.bothrametals.cardscanner.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.EventGroup
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** A problem the user should see, already in plain words. */
class CloudException(message: String, val offline: Boolean = false) : Exception(message)

/**
 * Everything that talks to the company's free Firebase project:
 * login (Google / email + password), the team (admins and staff) and the card backup.
 *
 * If the Firebase setup file (google-services.json) hasn't been added yet, [isConfigured] is false
 * and the app simply works on the phone only, like before.
 */
class CloudService(private val context: Context) {

    val isConfigured: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val user: FirebaseUser? get() = if (isConfigured) auth.currentUser else null
    val email: String? get() = user?.email?.let { CloudMapper.normalizeEmail(it) }

    fun isVerified(): Boolean {
        val u = user ?: return false
        return u.isEmailVerified || u.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    }

    // ------------------------------------------------------------------ login

    /** Shows Google's account picker and returns an ID token. */
    suspend fun googleIdToken(activity: Activity): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) throw CloudException("Google sign-in is not switched on in Firebase yet. Use email and password, or see the setup guide.")
        val option = GetSignInWithGoogleOption.Builder(context.getString(resId)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        try {
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val c = result.credential
            if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return GoogleIdTokenCredential.createFrom(c.data).idToken
            }
            throw CloudException("Google didn't return an account. Please try again.")
        } catch (e: GetCredentialCancellationException) {
            throw CloudException("Sign-in cancelled")
        } catch (e: NoCredentialException) {
            throw CloudException("No Google account found on this phone. Add one in phone Settings, or use email and password.")
        } catch (e: GetCredentialException) {
            throw CloudException("Google sign-in failed: ${e.message ?: e.type}")
        }
    }

    suspend fun signInWithGoogle(idToken: String) = wrap {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun signInWithEmail(email: String, password: String) = wrap {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun createAccount(email: String, password: String) = wrap {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun resendVerification() = wrap { auth.currentUser?.sendEmailVerification()?.await() }

    /** Re-checks whether the user has clicked the link in the verification email. */
    suspend fun refreshVerified(): Boolean = wrap {
        val u = auth.currentUser ?: return@wrap false
        u.reload().await()
        val ok = auth.currentUser?.isEmailVerified == true
        if (ok) auth.currentUser?.getIdToken(true)?.await() // so the database sees "verified"
        ok
    }

    suspend fun sendPasswordReset(email: String) = wrap { auth.sendPasswordResetEmail(email.trim()).await() }

    fun signOut() {
        if (isConfigured) auth.signOut()
    }

    /** True when every change made on this phone has reached the cloud (waits up to [timeoutMs]). */
    suspend fun allChangesUploaded(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { db.waitForPendingWrites().await(); true } ?: false

    // ------------------------------------------------------------------ team

    data class Team(val exists: Boolean, val name: String = "", val admins: List<String> = emptyList())

    data class Member(val email: String, val name: String, val isAdmin: Boolean)

    suspend fun loadTeam(): Team = wrap {
        val snap = db.collection(CloudMapper.CONFIG).document(CloudMapper.TEAM_DOC).get().await()
        if (!snap.exists()) Team(false) else {
            @Suppress("UNCHECKED_CAST")
            val admins = (snap.get("admins") as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.lowercase() }
            Team(true, snap.getString("name").orEmpty(), admins)
        }
    }

    suspend fun isListedMember(email: String): Boolean = wrap {
        db.collection(CloudMapper.MEMBERS).document(email).get().await().exists()
    }

    /** First-time company setup. The person doing it becomes the admin. */
    suspend fun createTeam(name: String, email: String, displayName: String) = wrap {
        db.collection(CloudMapper.CONFIG).document(CloudMapper.TEAM_DOC).set(
            mapOf("name" to name, "admins" to listOf(email), "createdBy" to email, "createdAt" to System.currentTimeMillis())
        ).await()
        db.collection(CloudMapper.MEMBERS).document(email).set(
            mapOf("email" to email, "name" to displayName, "addedBy" to email, "addedAt" to System.currentTimeMillis())
        ).await()
    }

    suspend fun listMembers(team: Team): List<Member> = wrap {
        val snap = db.collection(CloudMapper.MEMBERS).get().await()
        val listed = snap.documents.map { d ->
            Member(d.id, d.getString("name").orEmpty(), d.id in team.admins)
        }
        val missingAdmins = team.admins.filter { a -> listed.none { it.email == a } }.map { Member(it, "", true) }
        (listed + missingAdmins).sortedWith(compareBy({ !it.isAdmin }, { it.email }))
    }

    suspend fun addMember(email: String, name: String, admin: Boolean, addedBy: String) = wrap {
        val e = CloudMapper.normalizeEmail(email)
        db.collection(CloudMapper.MEMBERS).document(e).set(
            mapOf("email" to e, "name" to name.trim(), "addedBy" to addedBy, "addedAt" to System.currentTimeMillis())
        ).await()
        val teamRef = db.collection(CloudMapper.CONFIG).document(CloudMapper.TEAM_DOC)
        teamRef.update("admins", if (admin) FieldValue.arrayUnion(e) else FieldValue.arrayRemove(e)).await()
    }

    suspend fun removeMember(email: String) = wrap {
        db.collection(CloudMapper.CONFIG).document(CloudMapper.TEAM_DOC).update("admins", FieldValue.arrayRemove(email)).await()
        db.collection(CloudMapper.MEMBERS).document(email).delete().await()
    }

    /** The company's shared free AI key (null if the admin hasn't added one). */
    suspend fun loadAiConfig(): Pair<String, List<String>>? = wrap {
        val snap = db.collection(CloudMapper.CONFIG).document(CloudMapper.AI_DOC).get().await()
        val key = snap.getString("geminiKey").orEmpty()
        @Suppress("UNCHECKED_CAST")
        val models = (snap.get("models") as? List<Any?>).orEmpty().mapNotNull { it as? String }
        if (key.isBlank()) null else key to models
    }

    suspend fun saveAiConfig(key: String, models: List<String>) = wrap {
        db.collection(CloudMapper.CONFIG).document(CloudMapper.AI_DOC)
            .set(mapOf("geminiKey" to key, "models" to models, "updatedAt" to System.currentTimeMillis())).await()
    }

    // ------------------------------------------------------------------ events

    /** Events this user can see: admins see all, everyone else the events they were added to. */
    suspend fun loadEvents(myEmail: String, isAdmin: Boolean): List<EventGroup> = wrap {
        val col = db.collection(CloudMapper.EVENTS)
        val snap = (if (isAdmin) col else col.whereArrayContains("members", myEmail)).get().await()
        snap.documents.mapNotNull { d -> d.data?.let { CloudMapper.eventFromMap(it) } }
    }

    /**
     * Creates or updates an event (works offline too; sent when the internet is back).
     * Only admins may change the sub-groups, so for everyone else they are left out and the
     * cloud keeps its own list (merge).
     */
    fun saveEvent(e: EventGroup, isAdmin: Boolean, onRejected: (Exception) -> Unit) {
        val data = CloudMapper.eventToMap(e).let { if (isAdmin) it else it - "subGroups" }
        db.collection(CloudMapper.EVENTS).document(e.id).set(data, SetOptions.merge())
            .addOnFailureListener { onRejected(it) }
    }

    // ------------------------------------------------------------------ cards

    /**
     * Sends a card (and its photos, if not sent yet) to the cloud.
     * Works offline too: Firebase keeps the change on the phone and sends it when the internet is back.
     */
    fun pushCard(r: CardRecord, imageBytes: (back: Boolean) -> ByteArray?, onRejected: (Exception) -> Unit) {
        val batch = db.batch()
        val data = HashMap<String, Any?>(CloudMapper.toMap(r))
        data["syncAt"] = FieldValue.serverTimestamp()
        batch.set(db.collection(CloudMapper.CARDS).document(r.id), data)
        if (!r.imagesUploaded) {
            for (back in listOf(false, true)) {
                val bytes = imageBytes(back) ?: continue
                batch.set(
                    db.collection(CloudMapper.CARD_IMAGES).document(CloudMapper.imageId(r.id, back)),
                    mapOf(
                        "cardId" to r.id,
                        "ownerEmail" to r.ownerEmail,
                        "visibility" to r.visibility,
                        "data" to Blob.fromBytes(bytes),
                        "updatedAt" to r.updatedAt,
                    ),
                )
            }
        }
        batch.commit().addOnFailureListener { onRejected(it) }
    }

    fun pushDelete(r: CardRecord) {
        val batch = db.batch()
        val data = HashMap<String, Any?>(CloudMapper.tombstone(r, System.currentTimeMillis()))
        data["syncAt"] = FieldValue.serverTimestamp()
        batch.set(db.collection(CloudMapper.CARDS).document(r.id), data)
        batch.delete(db.collection(CloudMapper.CARD_IMAGES).document(CloudMapper.imageId(r.id, false)))
        batch.delete(db.collection(CloudMapper.CARD_IMAGES).document(CloudMapper.imageId(r.id, true)))
        batch.commit()
    }

    class Pull(val items: List<CloudMapper.Remote>, val newest: Timestamp?, val complete: Boolean)

    /**
     * Gets cards that changed since [since] (null = everything).
     * Staff get the company's shared cards plus their own private ones; admins get everything.
     */
    suspend fun pullCards(myEmail: String, isAdmin: Boolean, since: Timestamp?): Pull = wrap {
        val cards = db.collection(CloudMapper.CARDS)
        fun Query.after(): Query = if (since != null) whereGreaterThan("syncAt", since) else this
        val queries: List<Query> = if (isAdmin) listOf(cards.after()) else listOf(
            cards.whereEqualTo("visibility", CardRecord.VISIBILITY_COMPANY).after(),
            cards.whereEqualTo("ownerEmail", myEmail).after(),
        )
        val snaps = ArrayList<QuerySnapshot>()
        for (q in queries) {
            snaps += try {
                q.get().await()
            } catch (e: FirebaseFirestoreException) {
                // A missing database index only slows things down: fall back to a full download.
                if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION && since != null) {
                    return@wrap pullCards(myEmail, isAdmin, null)
                }
                throw e
            }
        }
        var newest: Timestamp? = since
        val byId = LinkedHashMap<String, CloudMapper.Remote>()
        for (s in snaps) for (d in s.documents) {
            val remote = d.data?.let { CloudMapper.fromMap(it) } ?: continue
            byId[remote.record.id] = remote
            val t = d.getTimestamp("syncAt")
            if (t != null && (newest == null || t > newest!!)) newest = t
        }
        val fromServer = snaps.all { !it.metadata.isFromCache }
        Pull(byId.values.toList(), newest, complete = since == null && fromServer)
    }

    suspend fun downloadImage(cardId: String, back: Boolean): ByteArray? = wrap {
        db.collection(CloudMapper.CARD_IMAGES).document(CloudMapper.imageId(cardId, back)).get().await()
            .getBlob("data")?.toBytes()
    }

    // ------------------------------------------------------------------ errors in plain words

    private suspend fun <T> wrap(block: suspend () -> T): T = try {
        block()
    } catch (e: CloudException) {
        throw e
    } catch (e: FirebaseNetworkException) {
        throw CloudException("No internet connection. Please try again when you're online.", offline = true)
    } catch (e: FirebaseAuthException) {
        throw CloudException(authMessage(e))
    } catch (e: FirebaseFirestoreException) {
        throw when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> CloudException("No internet connection. Your cards are safe on the phone and will back up later.", offline = true)
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> CloudException("Access denied. Ask your admin to add your email to the team.")
            else -> CloudException("Cloud error: ${e.message}")
        }
    }

    private fun authMessage(e: FirebaseAuthException): String = when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Email or password is wrong."
        "ERROR_USER_NOT_FOUND" -> "No account with this email. Tap \"Create account\"."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Tap \"Sign in\" instead, or \"Forgot password\"."
        "ERROR_WEAK_PASSWORD" -> "Please choose a longer password (at least 6 characters)."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait a few minutes and try again."
        "ERROR_USER_DISABLED" -> "This account has been switched off by the admin."
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "This email already signs in another way. Try \"Continue with Google\"."
        else -> e.message ?: "Sign-in failed"
    }
}
