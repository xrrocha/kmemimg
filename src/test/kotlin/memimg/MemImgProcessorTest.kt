package memimg

import arrow.core.getOrElse
import arrow.core.getOrHandle
import memimg.MemImgProcessor.CommandFailure
import memimg.MemImgProcessor.SystemFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MemImgProcessorTest {

    @Test
    fun `executes and serializes successful command`() {
        val system1 = mutableListOf<Data>()
        val eventStorage = MemoryEventStorage()
        val memimg1 = MemImgProcessor(system1, eventStorage)
        memimg1.execute(Data(0, "zero"))
        memimg1.execute(Data(1, "one"))
        memimg1.close()
        @Suppress("UNCHECKED_CAST")
        assertEquals(
            listOf(Data(0, "zero"), Data(1, "one")),
            eventStorage.buffer as List<Data>
        )
    }

    @Test
    fun `initializes from previous commands`() {
        val system1 = mutableListOf<Data>()
        val eventStorage = MemoryEventStorage()
        val memimg1 = MemImgProcessor(system1, eventStorage)
        memimg1.execute(Data(0, "zero"))
        memimg1.execute(Data(1, "one"))
        memimg1.close()

        val system2 = mutableListOf<Data>()
        MemImgProcessor(system2, eventStorage)
        assertEquals(system1, system2)
    }

    @Test
    fun `fails on error loading from previous commands`() {
        var flag = false

        class DummyCommand : Command {
            override fun applyTo(system: Any): Any? {
                if (flag) {
                    throw Exception("Kaboom!")
                }
                return null
            }
        }

        val eventStorage = MemoryEventStorage()
        val memimg = MemImgProcessor("system", eventStorage)
        memimg.execute(DummyCommand())
        assertThrows<Exception> {
            flag = true
            MemImgProcessor("system", eventStorage)
        }
    }

    @Test
    fun `executes query`() {
        val system1 = mutableListOf<Data>()
        val eventStorage = MemoryEventStorage()
        val memimg1 = MemImgProcessor(system1, eventStorage)
        memimg1.execute(Data(0, "zero"))
        memimg1.execute(Data(1, "one"))
        val result = memimg1.execute(object : Query {
            override fun extractFrom(system: Any): Any? =
                @Suppress("UNCHECKED_CAST")
                (system as MutableList<Data>)
                    .find { it.id == 1 }
        })
        assertTrue(result.isRight())
        assertEquals(Data(1, "one"), result.getOrElse { null })
    }

    @Test
    fun `signals failure on failed query`() {
        val system1 = mutableListOf<Data>()
        val eventStorage = MemoryEventStorage()
        val memimg1 = MemImgProcessor(system1, eventStorage)
        memimg1.execute(Data(0, "zero"))
        memimg1.execute(Data(1, "one"))
        val result = memimg1.execute(object : Query {
            override fun extractFrom(system: Any): Any? =
                throw Exception("Kaboom!")
        })
        assertTrue(result.isLeft())
        assertTrue(result.getOrHandle { it } is CommandFailure)
        assertEquals("Kaboom!", result.getOrHandle { it.throwable.message })
    }

    @Test
    fun `rolls back partial updates on failed command`() {
        var cnt = 0
        var flag = false

        class DummyCommand : Command {
            override fun applyTo(system: Any): Any? {
                TxManager.remember(this, "cnt", cnt) { cnt = it }
                cnt += 1
                if (flag) {
                    throw Exception("Kaboom!")
                }
                return null
            }
        }

        val eventStorage = MemoryEventStorage()
        val memimg = MemImgProcessor("system", eventStorage)
        memimg.execute(DummyCommand())
        assertEquals(1, cnt)
        memimg.execute(DummyCommand())
        assertEquals(2, cnt)
        flag = true
        memimg.execute(DummyCommand())
            .map { fail() }
            .mapLeft { assertEquals(2, cnt) }
    }

    @Test
    fun `fails severely on serialization failure`() {
        class DummyCommand : Command {
            override fun applyTo(system: Any): Any? {
                return null
            }
        }

        val eventStorage = object : EventStorage {
            override fun <E> replay(eventConsumer: (E) -> Unit) {}
            override fun append(event: Any) {
                throw Exception("Kaboom!")
            }
        }
        val memimg = MemImgProcessor("system", eventStorage)
        memimg.execute(DummyCommand())
            .map { fail() }
            .mapLeft {
                assertTrue(it is SystemFailure)
            }
    }

    @Test
    fun `fails severely on rollback failure`() {
        class DummyCommand : Command {
            override fun applyTo(system: Any): Any? {
                TxManager.remember(this, "cnt", 0) {
                    throw Exception("Kaboom during rollback!")
                }
                throw Exception("Kaboom during command execution!")
            }
        }

        val eventStorage = MemoryEventStorage()
        val memimg = MemImgProcessor("system", eventStorage)
        memimg.execute(DummyCommand())
        memimg.execute(DummyCommand())
            .map { fail() }
            .mapLeft { assertTrue(it is SystemFailure) }
    }

    @Test
    fun `closes underlying event storage on close`() {
    }
}