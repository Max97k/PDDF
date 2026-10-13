package com.max97k.pddf.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.max97k.pddf.MainViewModel
import com.max97k.pddf.feature.decrypt.PDFDecryptorScreen

/**
 * Main NavHost for PDDF application.
 * Manages top-level type-safe navigation routes while strictly adhering to the Fragment & Window
 * Isolation Contract (AndroidX PdfViewerFragment and dialog overlays share the host window).
 */
@Composable
fun PddfNavHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    NavHost(
        navController = navController,
        startDestination = PddfNavDestination.Decrypt,
        modifier = modifier
    ) {
        composable<PddfNavDestination.Decrypt> {
            PDFDecryptorScreen(
                viewModel = viewModel,
                modifier = Modifier,
                snackbarHostState = snackbarHostState,
                windowWidthSizeClass = windowWidthSizeClass
            )
        }
        composable<PddfNavDestination.SavedPasswords> {
            PDFDecryptorScreen(
                viewModel = viewModel,
                modifier = Modifier,
                snackbarHostState = snackbarHostState,
                windowWidthSizeClass = windowWidthSizeClass
            )
        }
        composable<PddfNavDestination.Settings> {
            PDFDecryptorScreen(
                viewModel = viewModel,
                modifier = Modifier,
                snackbarHostState = snackbarHostState,
                windowWidthSizeClass = windowWidthSizeClass
            )
        }
        composable<PddfNavDestination.Viewer> {
            PDFDecryptorScreen(
                viewModel = viewModel,
                modifier = Modifier,
                snackbarHostState = snackbarHostState,
                windowWidthSizeClass = windowWidthSizeClass
            )
        }
    }
}
