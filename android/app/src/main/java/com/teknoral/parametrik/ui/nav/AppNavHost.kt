package com.teknoral.parametrik.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.teknoral.parametrik.data.media.CropRect
import com.teknoral.parametrik.ui.StartDestination
import com.teknoral.parametrik.ui.capture.CameraCaptureScreen
import com.teknoral.parametrik.ui.capture.CropScreen
import com.teknoral.parametrik.ui.jobs.JobListScreen
import com.teknoral.parametrik.ui.login.LoginScreen
import com.teknoral.parametrik.ui.params.ParamsScreen
import com.teknoral.parametrik.ui.result.ResultScreen
import com.teknoral.parametrik.ui.server.ServerSettingsScreen

@Composable
fun AppNavHost(start: StartDestination) {
    val navController = rememberNavController()

    // Kamera → kırpma → iş listesi akışının sonucu.
    var pendingUpload by remember { mutableStateOf<Pair<Uri, CropRect>?>(null) }

    val startRoute = when (start) {
        StartDestination.SERVER -> Routes.SERVER
        StartDestination.LOGIN -> Routes.LOGIN
        StartDestination.JOBS -> Routes.JOBS
    }

    fun goToLogin() {
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    fun goToJobs() {
        navController.navigate(Routes.JOBS) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = startRoute) {

        composable(Routes.SERVER) {
            ServerSettingsScreen(
                onContinue = { sessionOpen -> if (sessionOpen) goToJobs() else goToLogin() },
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onLoggedOut = { goToLogin() }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { goToJobs() },
                onServerSettings = { navController.navigate(Routes.SERVER) }
            )
        }

        composable(Routes.JOBS) {
            JobListScreen(
                onOpenJob = { jobId -> navController.navigate(Routes.params(jobId)) },
                onOpenCamera = { navController.navigate(Routes.CAMERA) },
                onOpenSettings = { navController.navigate(Routes.SERVER) },
                onAuthRequired = { goToLogin() },
                pendingFromCamera = pendingUpload,
                onPendingConsumed = { pendingUpload = null }
            )
        }

        composable(Routes.CAMERA) {
            CameraCaptureScreen(
                onCaptured = { uri -> navController.navigate(Routes.crop(uri)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CROP,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { entry ->
            val encoded = entry.arguments?.getString("uri").orEmpty()
            val uri = remember(encoded) { Uri.parse(Uri.decode(encoded)) }
            CropScreen(
                imageUri = uri,
                onConfirm = { confirmedUri, crop ->
                    pendingUpload = confirmedUri to crop
                    navController.popBackStack(Routes.JOBS, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PARAMS,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) {
            ParamsScreen(
                onBack = { navController.popBackStack() },
                onSliced = { jobId, _ ->
                    navController.navigate(Routes.result(jobId)) {
                        popUpTo(Routes.PARAMS) { inclusive = true }
                    }
                },
                onAuthRequired = { goToLogin() }
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) {
            ResultScreen(
                onBack = {
                    if (!navController.popBackStack()) goToJobs()
                },
                onEditParams = { jobId ->
                    navController.navigate(Routes.params(jobId)) {
                        popUpTo(Routes.RESULT) { inclusive = true }
                    }
                },
                onAuthRequired = { goToLogin() }
            )
        }
    }
}
