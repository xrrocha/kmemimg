package memimg

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextFileEventStorageTest {

    private val file: File = File.createTempFile("eventStorage", ".json").apply {
        deleteOnExit()
    }

    @BeforeEach
    fun deleteFile() {
        file.delete()
    }

    @Test
    fun `creates new, empty file`() {
        val eventStorage = TextFileEventStorage(file, DataParser)
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
        eventStorage.replay<Data> { }
        assertEquals(0L, file.length())
        file.delete()
    }

    @Test
    fun `fails on bad directory permissions`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        val badDir = File(tmpDir, UUID.randomUUID().toString()).apply {
            deleteOnExit()
        }
        assertFalse(badDir.exists() || badDir.isDirectory)
        assertTrue(badDir.mkdir())
        badDir.setWritable(false)
        val badFile = File(badDir, "bad.json")
        assertThrows<IllegalArgumentException> {
            TextFileEventStorage(badFile, DataParser)
        }
    }

    @Test
    fun `appends command`() {
        val eventStorage = TextFileEventStorage(file, DataParser)
        eventStorage.replay<Data> { }
        eventStorage.append(Data(0, "zero"))
        eventStorage.append(Data(1, "one"))
        eventStorage.close()
        assertEquals(
            listOf(Data(0, "zero"), Data(1, "one")),
            file.readLines().map(DataParser::parse)
        )
    }

    @Test
    fun `replays previous commands`() {
        val eventStorage = TextFileEventStorage(file, DataParser)
        eventStorage.replay<Data> { }
        eventStorage.append(Data(0, "zero"))
        eventStorage.append(Data(1, "one"))
        eventStorage.close()
        val system = mutableListOf<Data>()
        eventStorage.replay(system::add)
        assertEquals(
            listOf(Data(0, "zero"), Data(1, "one")),
            system
        )
        eventStorage.close()
    }
}