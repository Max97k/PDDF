package com.max97k.pddf

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.max97k.pddf.data.SecurityPreferences
import com.max97k.pddf.feature.decrypt.PDFDecryptorScreen
import com.max97k.pddf.feature.vault.BiometricHelper
import com.max97k.pddf.ui.navigation.PddfNavHost
import com.max97k.pddf.ui.theme.MyApplicationTheme
import com.max97k.pddf.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                FileUtils.takePersistableUriPermissionSafely(
                    this,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.onAction(MainUiAction.SelectFiles(uris))
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        FileUtils.cleanUpDecryptedCache(this)

        // Apply FLAG_SECURE synchronously before setContent{} so that the very first
        // frame and the Recent-Tasks thumbnail (captured during onPause) are already
        // protected when the user has the setting enabled.
        if (SecurityPreferences.readScreenshotProtectionSync(this)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }

        enableEdgeToEdge()
        setupDynamicShortcuts()
        handleIntent(intent)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode) {
                val snackbarHostState = remember { SnackbarHostState() }

                // Handle TriggerBiometricAuth effect here in Activity scope so
                // BiometricPrompt has access to the correct FragmentActivity context.
                LaunchedEffect(Unit) {
                    viewModel.uiEffect.collect { effect ->
                        if (effect is UiEffect.TriggerBiometricAuth) {
                            BiometricHelper.authenticate(
                                activity = this@MainActivity,
                                title = getString(R.string.biometric_prompt_title),
                                subtitle = getString(R.string.biometric_prompt_subtitle),
                                onSuccess = {
                                    viewModel.onAction(MainUiAction.SetPasswordListDialogVisible(true))
                                },
                                onError = { errorCode, errString ->
                                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                    ) {
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            errString,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }

                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        PddfNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            snackbarHostState = snackbarHostState,
                            windowWidthSizeClass = windowSizeClass.widthSizeClass
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
        FileUtils.cleanUpDecryptedCache(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        viewModel.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        viewModel.onUserInteraction()
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupDynamicShortcuts() {
        val shortcutManager = getSystemService(ShortcutManager::class.java)
        if (shortcutManager != null) {
            val selectPdfShortcut = ShortcutInfo.Builder(this, "shortcut_select_pdf")
                .setShortLabel(getString(R.string.shortcut_select_pdf_short))
                .setLongLabel(getString(R.string.shortcut_select_pdf_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_select_pdf))
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = "com.max97k.pddf.ACTION_SELECT_PDF"
                })
                .build()

            val savedPasswordsShortcut = ShortcutInfo.Builder(this, "shortcut_saved_passwords")
                .setShortLabel(getString(R.string.shortcut_saved_passwords_short))
                .setLongLabel(getString(R.string.shortcut_saved_passwords_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_saved_passwords))
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = "com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS"
                })
                .build()

            val batchProcessShortcut = ShortcutInfo.Builder(this, "shortcut_batch_process")
                .setShortLabel(getString(R.string.title_processing_batch))
                .setLongLabel(getString(R.string.title_processing_batch))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_select_pdf)) // Use existing icon for batch
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = "com.max97k.pddf.ACTION_BATCH_PROCESS"
                })
                .build()

            shortcutManager.dynamicShortcuts = listOf(selectPdfShortcut, savedPasswordsShortcut, batchProcessShortcut)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        if (action == "com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS") {
            viewModel.onAction(MainUiAction.SetPasswordListDialogVisible(true))
            return
        } else if (action == "com.max97k.pddf.ACTION_SELECT_PDF" || action == "com.max97k.pddf.ACTION_BATCH_PROCESS") {
            viewModel.onAction(MainUiAction.RequestFilePicker)
            pickPdfLauncher.launch(arrayOf("application/pdf"))
            return
        }

        val uris = mutableListOf<Uri>()

        if (Intent.ACTION_VIEW == action) {
            intent.data?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND == action) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Parcelable::class.java) as? Uri
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            if (uri != null) {
                uris.add(uri)
            } else {
                intent.clipData?.let { clipData ->
                    if (clipData.itemCount > 0) {
                        clipData.getItemAt(0).uri?.let { uris.add(it) }
                    }
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Parcelable::class.java)?.mapNotNull { it as? Uri }
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.mapNotNull { it as? Uri }
            }
            if (!streamUris.isNullOrEmpty()) {
                uris.addAll(streamUris)
            } else {
                intent.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
            }
        }

        if (uris.isNotEmpty()) {
            viewModel.onAction(MainUiAction.SelectFiles(uris))
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    return FileUtils.getFileName(context, uri)
}
