package com.adityanotes.core.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.adityanotes.feature.notebook.presentation.PageListScreen
import com.adityanotes.feature.notebook.presentation.PageViewModel

object AdityaNotesRoutes {
    const val NOTEBOOKS = "notebooks"
    const val PAGES = "pages/{notebookId}"

    fun pages(notebookId: Long): String {
        return "pages/$notebookId"
    }
}

@Composable
fun AdityaNotesNavHost(
    navController: NavHostController,
    startDestination: String = AdityaNotesRoutes.NOTEBOOKS,
    notebookScreen: @Composable () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = AdityaNotesRoutes.NOTEBOOKS
        ) {
            notebookScreen()
        }

        composable(
            route = AdityaNotesRoutes.PAGES,
            arguments = listOf(
                navArgument("notebookId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->

            val notebookId =
                backStackEntry.arguments?.getLong("notebookId")
                    ?: return@composable

            val viewModel: PageViewModel = hiltViewModel()

            PageListScreen(
                notebookId = notebookId,
                viewModel = viewModel
            )
        }
    }
}