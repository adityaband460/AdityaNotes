package com.adityanotes.feature.notebook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityanotes.core.database.entity.FolderEntity
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.feature.notebook.data.NotebookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SidebarItem {
    data object AllNotes : SidebarItem
    data class Folder(val folder: FolderEntity) : SidebarItem
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val repository: NotebookRepository
) : ViewModel() {

    // Selected sidebar section: defaults to AllNotes
    private val _selectedSidebarItem = MutableStateFlow<SidebarItem>(SidebarItem.AllNotes)
    val selectedSidebarItem: StateFlow<SidebarItem> = _selectedSidebarItem.asStateFlow()

    // Current navigation hierarchy for breadcrumbs
    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _breadcrumbStack = MutableStateFlow<List<FolderEntity>>(emptyList())
    val breadcrumbStack: StateFlow<List<FolderEntity>> = _breadcrumbStack.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // All folders created by the user
    val allFolders: StateFlow<List<FolderEntity>> = repository.getRootFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Reactive folders in current directory
    val folders: StateFlow<List<FolderEntity>> = _currentFolderId
        .flatMapLatest { folderId ->
            if (folderId == null) {
                repository.getRootFolders()
            } else {
                repository.getSubFolders(folderId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Reactive notebooks in current directory or selected section
    val notebooks: StateFlow<List<NotebookEntity>> = _selectedSidebarItem
        .flatMapLatest { item ->
            when (item) {
                is SidebarItem.Folder -> repository.getNotebooksInFolder(item.folder.id)
                SidebarItem.AllNotes -> repository.getAllNotebooks()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Search results across entire library
    val searchResults: StateFlow<List<NotebookEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                MutableStateFlow(emptyList())
            } else {
                repository.searchNotebooks(query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Total counts for badges
    val allNotebooks: StateFlow<List<NotebookEntity>> = repository.getAllNotebooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun selectSidebarItem(item: SidebarItem) {
        _selectedSidebarItem.value = item
        when (item) {
            is SidebarItem.Folder -> {
                _currentFolderId.value = item.folder.id
                _breadcrumbStack.value = listOf(item.folder)
            }
            SidebarItem.AllNotes -> {
                _currentFolderId.value = null
                _breadcrumbStack.value = emptyList()
            }
        }
        _searchQuery.value = ""
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openFolder(folder: FolderEntity) {
        _selectedSidebarItem.value = SidebarItem.Folder(folder)
        _currentFolderId.value = folder.id
        _breadcrumbStack.value = _breadcrumbStack.value + folder
        _searchQuery.value = ""
    }

    fun navigateToBreadcrumb(targetFolderIndex: Int) {
        if (targetFolderIndex < 0) {
            _currentFolderId.value = null
            _breadcrumbStack.value = emptyList()
            _selectedSidebarItem.value = SidebarItem.AllNotes
        } else {
            val currentList = _breadcrumbStack.value
            if (targetFolderIndex in currentList.indices) {
                val targetFolder = currentList[targetFolderIndex]
                _currentFolderId.value = targetFolder.id
                _breadcrumbStack.value = currentList.subList(0, targetFolderIndex + 1)
                _selectedSidebarItem.value = SidebarItem.Folder(targetFolder)
            }
        }
        _searchQuery.value = ""
    }

    fun navigateUp(): Boolean {
        val currentList = _breadcrumbStack.value
        return if (currentList.isNotEmpty()) {
            val newList = currentList.dropLast(1)
            _breadcrumbStack.value = newList
            val parent = newList.lastOrNull()
            _currentFolderId.value = parent?.id
            _selectedSidebarItem.value = if (parent != null) SidebarItem.Folder(parent) else SidebarItem.AllNotes
            _searchQuery.value = ""
            true
        } else {
            false
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createFolder(
                name = name.trim(),
                parentFolderId = _currentFolderId.value
            )
            val newFolder = FolderEntity(
                id = newId,
                name = name.trim(),
                parentFolderId = _currentFolderId.value,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            selectSidebarItem(SidebarItem.Folder(newFolder))
        }
    }

    fun renameFolder(folder: FolderEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateFolder(folder.copy(name = newName.trim()))
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            selectSidebarItem(SidebarItem.AllNotes)
        }
    }

    fun createNotebook(
        name: String,
        coverColor: Long = 0xFF1E3A8AL,
        template: String = "RULED",
        isDark: Boolean = false,
        onCreated: (Long) -> Unit = {}
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val currentFolder = when (val item = _selectedSidebarItem.value) {
                is SidebarItem.Folder -> item.folder.id
                else -> _currentFolderId.value
            }
            val newId = repository.createNotebook(
                name = name.trim(),
                folderId = currentFolder,
                coverColor = coverColor,
                initialTemplate = template,
                isDarkPaper = isDark
            )
            onCreated(newId)
        }
    }

    fun renameNotebook(notebook: NotebookEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(name = newName.trim()))
        }
    }

    fun changeNotebookCover(notebook: NotebookEntity, newColor: Long) {
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(coverColor = newColor))
        }
    }

    fun deleteNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.deleteNotebook(notebook)
        }
    }
}
