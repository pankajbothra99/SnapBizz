package com.bothrametals.cardscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared frame for all the login-flow screens: blue header with the logo, then the form. */
@Composable
private fun AuthFrame(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2E6DA8), SteelDeep)))
                .statusBarsPadding()
                .padding(top = 28.dp, bottom = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLogo(Modifier.size(80.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Card Scanner", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    "Scan  ·  Read  ·  Save  ·  Share",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun Messages(vm: AppViewModel) {
    vm.authError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    vm.authInfo?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    if (vm.authBusy) CircularProgressIndicator(Modifier.size(28.dp))
}

@Composable
fun LoginScreen(vm: AppViewModel, onGoogle: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }

    AuthFrame(
        title = if (creating) "Create your account" else "Sign in",
        subtitle = "Your cards are backed up to your company account, so nothing is lost if you change or reset your phone.",
    ) {
        Button(onClick = onGoogle, enabled = !vm.authBusy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Continue with Google", fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(Modifier.weight(1f))
            Text("  or use email  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (creating) "Choose a password (6+ characters)" else "Password") },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "Hide" else "Show") } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { if (creating) vm.createAccount(email, password) else vm.signInWithEmail(email, password) },
            enabled = !vm.authBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(if (creating) "Create account" else "Sign in with email") }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { creating = !creating; vm.authError = null; vm.authInfo = null }) {
                Text(if (creating) "I already have an account" else "New here? Create account")
            }
            if (!creating) TextButton(onClick = { vm.forgotPassword(email) }) { Text("Forgot password?") }
        }
        Messages(vm)
    }
}

@Composable
fun VerifyEmailScreen(vm: AppViewModel, email: String) {
    AuthFrame(
        title = "Check your email",
        subtitle = "We sent a link to $email. Open it to confirm the email is yours, then come back and tap the button below. Check the spam folder if you don't see it.",
    ) {
        Button(onClick = { vm.checkVerified() }, enabled = !vm.authBusy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("I've confirmed my email")
        }
        OutlinedButton(onClick = { vm.resendVerification() }, enabled = !vm.authBusy, modifier = Modifier.fillMaxWidth()) {
            Text("Send the email again")
        }
        TextButton(onClick = { vm.doSignOut() }) { Text("Use a different account") }
        Messages(vm)
    }
}

@Composable
fun CompanySetupScreen(vm: AppViewModel, email: String) {
    var name by rememberSaveable { mutableStateOf("Bothra Metals & Alloys Limited") }
    AuthFrame(
        title = "Set up your company",
        subtitle = "This is the first login for your company. You ($email) will be the admin, and next you can add your team in Settings → Team.",
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Company name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.createCompany(name) }, enabled = !vm.authBusy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Create company and continue")
        }
        TextButton(onClick = { vm.doSignOut() }) { Text("Use a different account") }
        Messages(vm)
    }
}

@Composable
fun WaitingForAccessScreen(vm: AppViewModel, email: String) {
    AuthFrame(
        title = "Waiting for access",
        subtitle = "You're signed in as $email. Ask your admin to add this email in Card Scanner → Settings → Team, then tap Check again.",
    ) {
        Button(onClick = { vm.retryMembership() }, enabled = !vm.authBusy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Check again")
        }
        TextButton(onClick = { vm.doSignOut() }) { Text("Sign out") }
        Messages(vm)
    }
}

@Composable
fun AuthProblemScreen(vm: AppViewModel, message: String) {
    AuthFrame(title = "Couldn't connect", subtitle = message) {
        Button(onClick = { vm.retryMembership() }, enabled = !vm.authBusy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Try again")
        }
        TextButton(onClick = { vm.doSignOut() }) { Text("Sign out") }
        Messages(vm)
    }
}

@Composable
fun StartingScreen() {
    AuthFrame(title = "Signing you in…", subtitle = null) {
        CircularProgressIndicator()
    }
}
