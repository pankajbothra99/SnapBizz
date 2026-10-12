package com.bothrametals.cardscanner.ui

/** Where the user is in the login / team flow. */
sealed interface AuthState {
    /** Firebase isn't set up yet: the app works on this phone only (no login). */
    data object LocalOnly : AuthState
    /** Checking the login and team, first time on this phone. */
    data object Starting : AuthState
    data object SignedOut : AuthState
    /** Signed up with email + password; waiting for the user to click the link in the email. */
    data class VerifyEmail(val email: String) : AuthState
    /** Nobody has set up the company yet: this user can become the admin. */
    data class NoCompany(val email: String) : AuthState
    /** Signed in, but the admin hasn't added this email to the team. */
    data class NotMember(val email: String) : AuthState
    data class Member(val email: String, val name: String, val isAdmin: Boolean, val teamName: String) : AuthState
    /** Couldn't check (e.g. no internet on the very first login). */
    data class Problem(val message: String) : AuthState
}
