package com.adityanotes

import com.adityanotes.feature.page.presentation.PageViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.adityanotes.core.navigation.AdityaNotesNavHost
import com.adityanotes.core.navigation.AdityaNotesRoutes
import com.adityanotes.feature.notebook.presentation.NotebookViewModel
import com.adityanotes.ui.theme.AdityaNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: NotebookViewModel by viewModels()
    private val pageViewModel: PageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AdityaNotesTheme {
                val navController = rememberNavController()

                AdityaNotesNavHost(
                    navController = navController,
                    pageViewModel = pageViewModel,
                    notebookScreen = {
                        NotebookHomeScreen(
                            viewModel = viewModel,
                            onNotebookClick = { notebookId ->
                                navController.navigate(
                                    AdityaNotesRoutes.pages(notebookId)
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookHomeScreen(
    viewModel: NotebookViewModel,
    onNotebookClick: (Long) -> Unit
) {
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("AdityaNotes")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showCreateDialog = true
                }
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->

        if (notebooks.isEmpty()) {
            EmptyNotebookState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = notebooks,
                    key = { it.id }
                ) { notebook ->

                    var showMenu by remember {
                        mutableStateOf(false)
                    }

                    var showDeleteDialog by remember {
                        mutableStateOf(false)
                    }

                    var showRenameDialog by remember {
                        mutableStateOf(false)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNotebookClick(notebook.id)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = notebook.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = "Created ${notebook.createdAt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        showMenu = true
                                    }
                                ) {
                                    Text(
                                        text = "⋮",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = {
                                        showMenu = false
                                    }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text("Rename")
                                        },
                                        onClick = {
                                            showMenu = false
                                            showRenameDialog = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text("Delete")
                                        },
                                        onClick = {
                                            showMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (showRenameDialog) {
                        RenameNotebookDialog(
                            currentName = notebook.name,
                            onDismiss = {
                                showRenameDialog = false
                            },
                            onRename = { newName ->
                                viewModel.renameNotebook(
                                    notebook = notebook,
                                    newName = newName
                                )
                                showRenameDialog = false
                            }
                        )
                    }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showDeleteDialog = false
                            },
                            title = {
                                Text("Delete notebook?")
                            },
                            text = {
                                Text(
                                    "Are you sure you want to delete " +
                                            "\"${notebook.name}\"?"
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteNotebook(notebook)
                                        showDeleteDialog = false
                                    }
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteDialog = false
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNotebookDialog(
            onDismiss = {
                showCreateDialog = false
            },
            onCreate = { name ->
                viewModel.createNotebook(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun EmptyNotebookState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No notebooks yet",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Create your first notebook",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CreateNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New notebook")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                label = {
                    Text("Notebook name")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()

                    if (trimmedName.isNotEmpty()) {
                        onCreate(trimmedName)
                    }
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameNotebookDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember {
        mutableStateOf(currentName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Rename notebook")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                label = {
                    Text("Notebook name")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()

                    if (trimmedName.isNotEmpty()) {
                        onRename(trimmedName)
                    }
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}