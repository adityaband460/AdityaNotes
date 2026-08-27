package com.adityanotes.feature.notebook.data

import com.adityanotes.core.database.dao.FolderDao
import com.adityanotes.core.database.dao.NotebookDao
import com.adityanotes.core.database.dao.PageDao
import com.adityanotes.core.database.entity.FolderEntity
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.core.database.entity.PageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val notebookDao: NotebookDao,
    private val folderDao: FolderDao,
    private val pageDao: PageDao
) {

    // --- Folder Operations ---
    fun getRootFolders(): Flow<List<FolderEntity>> = folderDao.getRootFolders()

    fun getSubFolders(parentFolderId: Long): Flow<List<FolderEntity>> = folderDao.getSubFolders(parentFolderId)

    suspend fun getFolderById(folderId: Long): FolderEntity? = folderDao.getFolderById(folderId)

    suspend fun createFolder(name: String, parentFolderId: Long? = null): Long {
        val now = System.currentTimeMillis()
        return folderDao.insertFolder(
            FolderEntity(
                name = name.trim(),
                parentFolderId = parentFolderId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateFolder(folder: FolderEntity) {
        folderDao.updateFolder(folder.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        folderDao.deleteFolder(folder)
    }

    fun getNotebookCountForFolder(folderId: Long): Flow<Int> = folderDao.getNotebookCountForFolder(folderId)

    // --- Notebook Operations ---
    fun getAllNotebooks(): Flow<List<NotebookEntity>> = notebookDao.getAllNotebooks()

    fun getRootNotebooks(): Flow<List<NotebookEntity>> = notebookDao.getRootNotebooks()

    fun getNotebooksInFolder(folderId: Long): Flow<List<NotebookEntity>> = notebookDao.getNotebooksInFolder(folderId)

    fun searchNotebooks(query: String): Flow<List<NotebookEntity>> = notebookDao.searchNotebooks(query)

    suspend fun getNotebookById(notebookId: Long): NotebookEntity? = notebookDao.getNotebookById(notebookId)

    fun getPageCountForNotebook(notebookId: Long): Flow<Int> = notebookDao.getPageCountForNotebook(notebookId)

    suspend fun createNotebook(
        name: String,
        folderId: Long? = null,
        coverColor: Long = 0xFF1E3A8AL,
        initialTemplate: String = "RULED",
        isDarkPaper: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        val notebookId = notebookDao.insertNotebook(
            NotebookEntity(
                name = name.trim(),
                folderId = folderId,
                coverColor = coverColor,
                createdAt = now,
                updatedAt = now
            )
        )
        // Automatically create Page 1 for the new notebook
        pageDao.insertPage(
            PageEntity(
                notebookId = notebookId,
                name = "Page 1",
                paperTemplate = initialTemplate,
                isDarkPaper = isDarkPaper,
                createdAt = now,
                updatedAt = now
            )
        )
        return notebookId
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        notebookDao.updateNotebook(notebook.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) {
        notebookDao.deleteNotebook(notebook)
    }
}
