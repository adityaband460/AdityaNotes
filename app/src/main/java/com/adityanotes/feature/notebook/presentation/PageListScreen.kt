package com.adityanotes.feature.notebook.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    notebookId: Long,
    viewModel: PageViewModel
) {
    val pages by viewModel
        .getPagesForNotebook(notebookId)
        .collectAsStateWithLifecycle()

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pages")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
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

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = page.name,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "Created ${page.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            TextButton(
                                onClick = {
                                    viewModel.deletePage(page)
                                }
                            ) {
                                Text("Delete")
                            }
                        }
                    }
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