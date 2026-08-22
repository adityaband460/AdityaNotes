package com.adityanotes.feature.page.presentation

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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityanotes.core.database.entity.PageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    notebookId: Long,
    viewModel: PageViewModel,
    onPageClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(notebookId) {
        viewModel.loadPages(notebookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pages")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Text("←")
                    }
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

        if (pages.isEmpty()) {
            EmptyPageState(
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
                    items = pages,
                    key = { it.id }
                ) { page ->

                    PageCard(
                        page = page,
                        onClick = {
                            onPageClick(page.id)
                        },
                        onRename = { newName ->
                            viewModel.renamePage(
                                page = page,
                                newName = newName
                            )
                        },
                        onDelete = {
                            viewModel.deletePage(page)
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePageDialog(
            onDismiss = {
                showCreateDialog = false
            },
            onCreate = { name ->
                viewModel.createPage(
                    notebookId = notebookId,
                    name = name
                )

                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PageCard(
    page: PageEntity,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    var showRenameDialog by remember {
        mutableStateOf(false)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = page.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Updated ${page.updatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(
                onClick = {
                    menuExpanded = true
                }
            ) {
                Text(
                    text = "⋮"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = {
                    menuExpanded = false
                }
            ) {
                DropdownMenuItem(
                    text = {
                        Text("Rename")
                    },
                    onClick = {
                        menuExpanded = false
                        showRenameDialog = true
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Delete")
                    },
                    onClick = {
                        menuExpanded = false
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    if (showRenameDialog) {
        RenamePageDialog(
            currentName = page.name,
            onDismiss = {
                showRenameDialog = false
            },
            onRename = { newName ->
                onRename(newName)
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
                Text("Delete page?")
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${page.name}\"?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
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

@Composable
private fun EmptyPageState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No pages yet",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Create your first page",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CreatePageDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New page")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                label = {
                    Text("Page name")
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
private fun RenamePageDialog(
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
            Text("Rename page")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                label = {
                    Text("Page name")
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