package com.adityanotes.feature.notebook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.feature.notebook.data.NotebookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val repository: NotebookRepository
) : ViewModel() {

    val notebooks: StateFlow<List<NotebookEntity>> =
        repository.getAllNotebooks()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun createNotebook(name: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            repository.createNotebook(
                NotebookEntity(
                    name = name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun renameNotebook(
        notebook: NotebookEntity,
        newName: String
    ) {
        viewModelScope.launch {
            repository.updateNotebook(
                notebook.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.deleteNotebook(notebook)
        }
    }
}