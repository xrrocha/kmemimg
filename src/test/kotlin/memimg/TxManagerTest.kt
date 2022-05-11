package memimg

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TxManagerTest {

    @Test
    fun `clears journal on begin`() {
        TxManager.begin()
        assertTrue(TxManager.journal.get().isEmpty())
    }

    @Test
    fun `remembers new value`() {
        var value = -1
        val setValue: (Int) -> Unit = { value = it }

        TxManager.begin()
        TxManager.remember(this, "that", 0, setValue)

        TxManager.journal.get()[Pair(this, "that")]!!.invoke()
        assertEquals(0, value)
    }

    @Test
    fun `retains first value when remembered multiple times`() {
        var value = -1
        val setValue: (Int) -> Unit = { value = it }

        TxManager.begin()
        TxManager.remember(this, "that", 1, setValue)
        TxManager.remember(this, "that", 2, setValue)
        TxManager.remember(this, "that", 3, setValue)

        TxManager.journal.get()[Pair(this, "that")]!!.invoke()
        assertEquals(1, value)
    }

    @Test
    fun `invokes all undo handlers on rollback`() {
        var value = 0
        val setValue1: (Int) -> Unit = { value += 1 }
        val setValue2: (Int) -> Unit = { value += 1 }
        val setValue3: (Int) -> Unit = { value += 1 }

        TxManager.begin()
        TxManager.remember(this, "p1", 0, setValue1)
        TxManager.remember(this, "p2", 0, setValue2)
        TxManager.remember(this, "p3", 0, setValue3)

        TxManager.rollback()
        assertEquals(3, value)
    }

    @Test
    fun `propagates error on rollback`() {
        val setValue1: (String) -> Unit = { }
        val setValue2: (String) -> Unit = { throw UnsupportedOperationException("Screw up!") }
        val setValue3: (String) -> Unit = { }

        TxManager.begin()
        TxManager.remember(this, "p1", "a", setValue1)
        TxManager.remember(this, "p2", "b", setValue2)
        TxManager.remember(this, "p3", "c", setValue3)

        assertThrows<UnsupportedOperationException> { TxManager.rollback() }
    }
}

class TxDelegateTest {

    @Test
    fun `validates on initialization`() {
        class Data {
            var name: String by TxDelegate("") { it.isNotEmpty() }
        }
        assertThrows<IllegalArgumentException> { Data() }
    }

    @Test
    fun `sets initial value`() {
        class Data(val id: Int) {
            var name: String by TxDelegate("John Doe") { it.isNotEmpty() }
        }
        assertEquals("John Doe", Data(0).name)
    }

    @Test
    fun `retrieves initial and modified values`() {
        class Data(val id: Int) {
            var name: String by TxDelegate("John Doe") { it.isNotEmpty() }
        }

        val data = Data(0)
        assertEquals("John Doe", data.name)
        data.name = "Janet Doe"
        assertEquals("Janet Doe", data.name)
    }

    @Test
    fun `validates, remembers and sets value`() {
        class Data(val id: Int) {
            var name: String by TxDelegate("John Doe") { it.isNotEmpty() }
        }

        val data = Data(0)
        TxManager.begin()
        assertEquals("John Doe", data.name)
        assertFalse(TxManager.journal.get().containsKey(Pair(data, "name")))
        data.name = "Janet Doe"
        assertEquals("Janet Doe", data.name)
        assertTrue(TxManager.journal.get().containsKey(Pair(data, "name")))
        assertEquals("Janet Doe", data.name)
    }

    @Test
    fun `restores remembered value`() {
        class Data(val id: Int) {
            var name: String by TxDelegate("John Doe") { it.isNotEmpty() }
        }

        val data = Data(0)
        TxManager.begin()
        assertEquals("John Doe", data.name)
        assertFalse(TxManager.journal.get().containsKey(Pair(data, "name")))
        data.name = "Janet Doe"
        assertEquals("Janet Doe", data.name)
        assertTrue(TxManager.journal.get().containsKey(Pair(data, "name")))
        assertEquals("Janet Doe", data.name)
        TxManager.rollback()
        assertEquals("John Doe", data.name)
    }
}
