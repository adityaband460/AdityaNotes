package com.adityanotes.feature.notebook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityanotes.core.database.entity.PageEntity
import com.adityanotes.feature.notebook.data.PageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PageViewModel @Inject constructor(
    private val repository: PageRepository
) : ViewModel() {

    fun getPagesForNotebook(
        notebookId: Long
    ): StateFlow<List<PageEntity>> {
        return repository
            .getPagesForNotebook(notebookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    fun createPage(
        notebookId: Long,
        name: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            repository.createPage(
                PageEntity(
                    notebookId = notebookId,
                    name = name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun renamePage(
        page: PageEntity,
        newName: String
    ) {
        viewModelScope.launch {
            repository.updatePage(
                page.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            repository.deletePage(page)
        }
    }
}