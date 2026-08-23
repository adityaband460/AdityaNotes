package com.adityanotes.feature.page.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adityanotes.core.database.AdityaNotesDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeRepositoryTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private var database: AdityaNotesDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "stroke-history-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun drawUndoRedoRemainAvailableAfterDatabaseReopen() = runBlocking {
        var repository = openRepository()
        val pageId = 42L
        val stroke = StrokeEntity(
            pageId = pageId,
            pointData = StrokePointCodec.encode(
                listOf(
                    StrokePoint(10f, 20f, 0.8f, 0),
                    StrokePoint(30f, 40f, 1.1f, 16)
                )
            ),
            color = 0xFF1A1A1AL,
            strokeWidth = 4f,
            tool = StrokeTool.PEN.name
        )

        repository.addStroke(stroke)
        assertEquals(1, database!!.strokeDao().getStrokesForPage(pageId).size)

        assertTrue(repository.undo(pageId))
        assertTrue(database!!.strokeDao().getStrokesForPage(pageId).isEmpty())

        assertTrue(repository.redo(pageId))
        assertEquals(1, database!!.strokeDao().getStrokesForPage(pageId).size)

        database!!.close()
        database = null
        repository = openRepository()

        assertTrue(repository.undo(pageId))
        assertTrue(database!!.strokeDao().getStrokesForPage(pageId).isEmpty())
        assertTrue(repository.redo(pageId))
        assertEquals(1, database!!.strokeDao().getStrokesForPage(pageId).size)
        assertFalse(repository.redo(pageId))
    }

    private fun openRepository(): StrokeRepository {
        database = Room.databaseBuilder(
            context,
            AdityaNotesDatabase::class.java,
            databaseName
        )
            .allowMainThreadQueries()
            .build()

        return StrokeRepository(database!!)
    }
}
