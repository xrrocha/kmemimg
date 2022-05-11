package memimg

import arrow.core.getOrElse
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoggingTest {

    @Test
    fun `loads existing resource`() {
        val result = openResource("logging.properties")
        assertTrue(result.isRight())
        assertNotNull(result.getOrElse { null })
    }

    @Test
    fun `fails on non-existent resource`() {
        val result = openResource("non/existent")
        assertTrue(result.isLeft())
    }
}