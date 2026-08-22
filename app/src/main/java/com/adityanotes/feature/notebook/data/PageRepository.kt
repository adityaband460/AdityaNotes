package com.adityanotes.feature.notebook.data

import com.adityanotes.core.database.dao.PageDao
import com.adityanotes.core.database.entity.PageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageRepository @Inject constructor(
    private val pageDao: PageDao
) {

    fun getPagesForNotebook(notebookId: Long): Flow<List<PageEntity>> {
        return pageDao.getPagesForNotebook(notebookId)
    }

    suspend fun getPageById(pageId: Long): PageEntity? {
        return pageDao.getPageById(pageId)
    }

    suspend fun createPage(page: PageEntity): Long {
        return pageDao.insertPage(page)
    }

    suspend fun updatePage(page: PageEntity) {
        pageDao.updatePage(page)
    }

    suspend fun deletePage(page: PageEntity) {
        pageDao.deletePage(page)
    }

    suspend fun deletePagesForNotebook(notebookId: Long) {
        pageDao.deletePagesForNotebook(notebookId)
    }
}