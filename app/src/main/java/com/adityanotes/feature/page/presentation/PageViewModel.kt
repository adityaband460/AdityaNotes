package com.adityanotes.feature.page.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityanotes.core.database.entity.PageEntity
import com.adityanotes.feature.page.data.PageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    private val repository: PageRepository
) : ViewModel() {

    private val selectedNotebookId = MutableStateFlow<Long?>(null)

    val pages: StateFlow<List<PageEntity>> =
        selectedNotebookId
            .flatMapLatest { notebookId ->
                if (notebookId == null) {
                    flowOf(emptyList())
                } else {
                    repository.getPagesForNotebook(notebookId)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _currentPage =
        MutableStateFlow<PageEntity?>(null)

    val currentPage: StateFlow<PageEntity?> =
        _currentPage.asStateFlow()

    fun loadPages(notebookId: Long) {
        selectedNotebookId.value = notebookId
    }

    fun getPage(pageId: Long) {
        viewModelScope.launch {
            _currentPage.value = repository.getPageById(pageId)
        }
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
                    content = "",
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

    fun updatePageContent(
        page: PageEntity,
        content: String
    ) {
        viewModelScope.launch {
            repository.updatePage(
                page.copy(
                    content = content,
                    updatedAt = System.currentTimeMillis()
                )
            )

            _currentPage.value =
                repository.getPageById(page.id)
        }
    }

    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            repository.deletePage(page)

            if (_currentPage.value?.id == page.id) {
                _currentPage.value = null
            }
        }
    }
}