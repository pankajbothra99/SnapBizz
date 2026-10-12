package com.bothrametals.cardscanner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import com.bothrametals.cardscanner.ui.AppTheme
import com.bothrametals.cardscanner.ui.OnboardingScreen
import com.bothrametals.cardscanner.ui.StatusBarIcons
import com.bothrametals.cardscanner.ui.AuthProblemScreen
import com.bothrametals.cardscanner.ui.AuthState
import com.bothrametals.cardscanner.ui.CompanySetupScreen
import com.bothrametals.cardscanner.ui.EventScreen
import com.bothrametals.cardscanner.ui.LoginScreen
import com.bothrametals.cardscanner.ui.StartingScreen
import com.bothrametals.cardscanner.ui.TeamScreen
import com.bothrametals.cardscanner.ui.VerifyEmailScreen
import com.bothrametals.cardscanner.ui.WaitingForAccessScreen
import com.bothrametals.cardscanner.ui.AppViewModel
import com.bothrametals.cardscanner.ui.HomeScreen
import com.bothrametals.cardscanner.ui.ReviewScreen
import com.bothrametals.cardscanner.ui.Screen
import com.bothrametals.cardscanner.ui.SettingsScreen
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** true while the scanner was opened to add a back side to the current card */
    private var scanningBackSide = false

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = result?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                if (scanningBackSide) vm.addBackSide(uris.first()) else vm.processNewScan(uris)
            }
        }
    }

    /** Pick many card photos at once (bulk import); back sides are paired automatically. */
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.importFromGallery(uris)
    }

    private val galleryBackLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addBackSide(uri)
    }

    private val contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        vm.onContactScreenClosed(res.resultCode == RESULT_OK)
    }

    private val saveCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.writeToUri(uri, vm.csvBytes())
    }

    /** Android 8–9 only: permission to put card photos in the Gallery (newer Android doesn't need it). */
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Without this permission card photos can't be copied to the Gallery")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askGalleryPermissionIfNeeded()
        setContent {
            AppTheme {
                val dark = isSystemInDarkTheme()
                if (!vm.onboardingDone) {
                    // Welcome pages, shown once after install
                    StatusBarIcons(lightIcons = dark)
                    OnboardingScreen(onDone = { vm.finishOnboarding() })
                } else {
                    val a = vm.auth
                    val inApp = a == AuthState.LocalOnly || a is AuthState.Member
                    // Blue header on Home and the login screens → white status-bar icons
                    StatusBarIcons(lightIcons = dark || !inApp || vm.screen == Screen.Home)
                    when (a) {
                        AuthState.Starting -> StartingScreen()
                        AuthState.SignedOut -> LoginScreen(vm, onGoogle = { vm.signInWithGoogle(this@MainActivity) })
                        is AuthState.VerifyEmail -> VerifyEmailScreen(vm, a.email)
                        is AuthState.NoCompany -> CompanySetupScreen(vm, a.email)
                        is AuthState.NotMember -> WaitingForAccessScreen(vm, a.email)
                        is AuthState.Problem -> AuthProblemScreen(vm, a.message)
                        AuthState.LocalOnly, is AuthState.Member -> MainScreens()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onAppResumed()
    }

    private fun askGalleryPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    @androidx.compose.runtime.Composable
    private fun MainScreens() {
                when (vm.screen) {
                    Screen.Home -> HomeScreen(
                        vm = vm,
                        onScan = { startScanner(pages = 2, backSide = false) },
                        onPickFromGallery = { pickFromGallery() },
                        onSaveCsv = { saveCsvLauncher.launch(vm.csvFileName()) },
                        onShareCsv = { launchSafely(vm.shareCsv()) },
                        onShareVcf = { launchSafely(vm.shareAllVcf()) },
                        onOpenUrl = { launchSafely(it) },
                        onOpenSettings = { vm.openSettings() },
                    )
                    Screen.Review -> {
                        BackHandler { vm.closeReview() }
                        ReviewScreen(
                            vm = vm,
                            onBack = { vm.closeReview() },
                            onSaveToContacts = {
                                vm.contactIntent()?.let { intent ->
                                    try {
                                        contactLauncher.launch(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        toast("No Contacts app found on this phone")
                                    }
                                }
                            },
                            onAddBackSide = { startScanner(pages = 1, backSide = true) },
                            onShareVcf = { vm.shareEditingVcf()?.let { launchSafely(it) } },
                            onLaunch = { launchSafely(it) },
                        )
                    }
                    Screen.Settings -> {
                        BackHandler { vm.closeSettings() }
                        SettingsScreen(
                            vm = vm,
                            onBack = { vm.closeSettings() },
                            onScanSetupQr = { scanSetupQr() },
                            onLaunch = { launchSafely(it) },
                        )
                    }
                    Screen.Team -> {
                        BackHandler { vm.closeTeam() }
                        TeamScreen(vm = vm, onBack = { vm.closeTeam() })
                    }
                    Screen.Event -> {
                        BackHandler { vm.closeManageEvent() }
                        EventScreen(vm = vm, onBack = { vm.closeManageEvent() })
                    }
                }
    }

    /**
     * Opens Google's document scanner: auto-detects the card edges, crops and straightens it.
     * Scan the front, tap "+" to add the back, then "Done".
     */
    private fun startScanner(pages: Int, backSide: Boolean) {
        scanningBackSide = backSide
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pages)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                toast("Scanner not available (${e.message}). Pick the card photos instead.")
                if (backSide) {
                    galleryBackLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    pickFromGallery()
                }
            }
    }

    /** Scans a colleague's setup QR (Google Play services scanner, no camera permission needed). */
    private fun scanSetupQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode -> vm.applySetupCode(barcode.rawValue.orEmpty()) }
            .addOnFailureListener { e -> toast("Could not scan: ${e.message}") }
    }

    private fun pickFromGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun launchSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app found to open this")
        } catch (e: Exception) {
            toast(e.message ?: "Could not open")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
