package com.imanhaikal.memo.data.receipt

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The store takes a directory and a decode lambda rather than a Context precisely so this
 * can be a plain JVM test — `BitmapFactory` is a stub off-device.
 */
class ReceiptStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val uri: Uri = mockk(relaxed = true)

    private fun store(
        directory: File = temp.root,
        decode: (Uri) -> ByteArray? = { BYTES }
    ) = ReceiptStore(directory = directory, decode = decode)

    @Test
    fun `save writes the bytes and returns a valid name`() = runTest {
        val store = store()

        val name = store.save(uri)

        assertNotNull(name)
        assertTrue(store.isValidName(name!!))
        assertArrayEqualsTo(BYTES, File(temp.root, name))
    }

    @Test
    fun `save returns null and writes nothing when the source cannot be decoded`() = runTest {
        val store = store(decode = { null })

        assertNull(store.save(uri))
        assertEquals(0, temp.root.listFiles()!!.size)
    }

    @Test
    fun `save gives every image its own name`() = runTest {
        val store = store()

        val first = store.save(uri)
        val second = store.save(uri)

        // Names must never be derived from the row, or replacing an image would reuse a
        // path and a cached bitmap could outlive the image it was decoded from.
        assertTrue(first != second)
        assertEquals(2, temp.root.listFiles()!!.size)
    }

    @Test
    fun `sweep deletes an unreferenced file older than the grace`() = runTest {
        val store = store()
        val orphan = writeAged("orphan.jpg", ageMillis = 60_000)

        store.sweepOrphans(referenced = emptySet(), graceMillis = 10_000)

        assertFalse(orphan.exists())
    }

    @Test
    fun `sweep keeps an unreferenced file younger than the grace`() = runTest {
        val store = store()
        // The attachment sitting in a dialog the user has not confirmed yet. Sweeping it
        // would delete the image out from under an open dialog.
        val inFlight = writeAged("in-flight.jpg", ageMillis = 0)

        store.sweepOrphans(referenced = emptySet(), graceMillis = 10_000)

        assertTrue(inFlight.exists())
    }

    @Test
    fun `sweep keeps a referenced file however old it is`() = runTest {
        val store = store()
        val kept = writeAged("kept.jpg", ageMillis = 400L * 24 * 60 * 60 * 1000)

        store.sweepOrphans(referenced = setOf("kept.jpg"), graceMillis = 10_000)

        assertTrue(kept.exists())
    }

    @Test
    fun `sweep with no grace deletes a just-written orphan`() = runTest {
        val store = store()
        val orphan = writeAged("fresh.jpg", ageMillis = 0)

        // What a confirmed "clear this budget" passes: the user expects the byte count to
        // drop immediately, and no attach flow can be open behind the Settings screen.
        store.sweepOrphans(referenced = emptySet(), graceMillis = 0)

        assertFalse(orphan.exists())
    }

    @Test
    fun `fileFor rejects any name that could escape the directory`() {
        val store = store()

        // Backup JSON is untrusted input; a path here would let the viewer's share action
        // hand out the database itself.
        assertNull(store.fileFor("../databases/memo_database"))
        assertNull(store.fileFor("../../x.jpg"))
        assertNull(store.fileFor("a/b.jpg"))
        assertNull(store.fileFor("evil.jpg"))
        assertNull(store.fileFor(""))
        assertNull(store.fileFor("1f7b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d.png"))
    }

    @Test
    fun `fileFor resolves a name the store itself produced`() = runTest {
        val store = store()
        val name = store.save(uri)!!

        assertEquals(File(temp.root, name), store.fileFor(name))
    }

    @Test
    fun `stats counts and sums what is on disk, and deleteAll empties it`() = runTest {
        val store = store()
        store.save(uri)
        store.save(uri)

        assertEquals(ReceiptStorageStats(2, BYTES.size.toLong() * 2), store.stats())

        store.deleteAll()

        assertEquals(ReceiptStorageStats.EMPTY, store.stats())
    }

    @Test
    fun `constructing against a missing directory touches no filesystem`() = runTest {
        val missing = File(temp.root, "not/created/yet")

        // MemoTestHarness builds a default store for every test that needs a ViewModel;
        // an init-time mkdirs would fire on all of them.
        val store = store(directory = missing)

        assertFalse(missing.exists())
        assertEquals(ReceiptStorageStats.EMPTY, store.stats())
        store.sweepOrphans(referenced = emptySet(), graceMillis = 0)
        store.deleteAll()
        assertFalse(missing.exists())
    }

    private fun writeAged(name: String, ageMillis: Long): File =
        File(temp.root, name).apply {
            writeBytes(BYTES)
            setLastModified(System.currentTimeMillis() - ageMillis)
        }

    private fun assertArrayEqualsTo(expected: ByteArray, file: File) {
        assertTrue(file.exists())
        assertTrue(expected.contentEquals(file.readBytes()))
    }

    private companion object {
        val BYTES = byteArrayOf(1, 2, 3, 4, 5)
    }
}
