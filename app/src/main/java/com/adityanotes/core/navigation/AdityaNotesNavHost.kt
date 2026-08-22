package com.adityanotes.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.adityanotes.feature.page.presentation.PageEditorScreen
import com.adityanotes.feature.page.presentation.PageScreen
import com.adityanotes.feature.page.presentation.PageViewModel

object AdityaNotesRoutes {

    const val NOTEBOOKS = "notebooks"

    const val PAGES = "pages/{notebookId}"

    fun pages(notebookId: Long): String {
        return "pages/$notebookId"
    }

    const val EDITOR = "editor/{pageId}"

    fun editor(pageId: Long): String {
        return "editor/$pageId"
    }
}

@Composable
fun AdityaNotesNavHost(
    navController: NavHostController,
    startDestination: String = AdityaNotesRoutes.NOTEBOOKS,
    notebookScreen: @Composable () -> Unit,
    pageViewModel: PageViewModel
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

            PageScreen(
                notebookId = notebookId,
                viewModel = pageViewModel,
                onPageClick = { pageId ->
                    navController.navigate(
                        AdityaNotesRoutes.editor(pageId)
                    )
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = AdityaNotesRoutes.EDITOR,
            arguments = listOf(
                navArgument("pageId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->

            val pageId =
                backStackEntry.arguments?.getLong("pageId")
                    ?: return@composable

            PageEditorScreen(
                pageId = pageId,
                viewModel = pageViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}