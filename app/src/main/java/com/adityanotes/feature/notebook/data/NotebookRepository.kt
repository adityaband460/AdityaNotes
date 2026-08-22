package com.adityanotes.feature.notebook.data

import com.adityanotes.core.database.dao.NotebookDao
import com.adityanotes.core.database.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val notebookDao: NotebookDao
) {

    fun getAllNotebooks(): Flow<List<NotebookEntity>> {
        return notebookDao.getAllNotebooks()
    }

    suspend fun getNotebookById(notebookId: Long): NotebookEntity? {
        return notebookDao.getNotebookById(notebookId)
    }

    suspend fun createNotebook(notebook: NotebookEntity): Long {
        return notebookDao.insertNotebook(notebook)
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        notebookDao.updateNotebook(notebook)
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) {
        notebookDao.deleteNotebook(notebook)
    }
}