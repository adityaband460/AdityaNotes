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

    const val NOTEBOOK_EDITOR = "notebook_editor/{notebookId}?pageId={pageId}"

    fun notebookEditor(notebookId: Long, pageId: Long? = null): String {
        return if (pageId != null) {
            "notebook_editor/$notebookId?pageId=$pageId"
        } else {
            "notebook_editor/$notebookId"
        }
    }

    const val PAGES = "pages/{notebookId}"

    fun pages(notebookId: Long): String {
        return notebookEditor(notebookId)
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
            route = AdityaNotesRoutes.NOTEBOOK_EDITOR,
            arguments = listOf(
                navArgument("notebookId") {
                    type = NavType.LongType
                },
                navArgument("pageId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->

            val notebookId =
                backStackEntry.arguments?.getLong("notebookId")
                    ?: return@composable

            val pageIdArg = backStackEntry.arguments?.getLong("pageId") ?: -1L
            val targetPageId = if (pageIdArg > 0) pageIdArg else null

            PageEditorScreen(
                notebookId = notebookId,
                initialPageId = targetPageId,
                viewModel = pageViewModel,
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
                notebookId = null,
                initialPageId = pageId,
                viewModel = pageViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}