package com.bothrametals.cardscanner

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.bothrametals.cardscanner.ui.AppTheme
import com.bothrametals.cardscanner.ui.AppViewModel
import com.bothrametals.cardscanner.ui.HomeScreen
import com.bothrametals.cardscanner.ui.ReviewScreen
import com.bothrametals.cardscanner.ui.Screen
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

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris ->
        if (uris.isNotEmpty()) vm.processNewScan(uris)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                when (vm.screen) {
                    Screen.Home -> HomeScreen(
                        vm = vm,
                        onScan = { startScanner(pages = 2, backSide = false) },
                        onPickFromGallery = { pickFromGallery() },
                        onSaveCsv = { saveCsvLauncher.launch(vm.csvFileName()) },
                        onShareCsv = { launchSafely(vm.shareCsv()) },
                        onShareVcf = { launchSafely(vm.shareAllVcf()) },
                        onOpenUrl = { launchSafely(it) },
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
                }
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
