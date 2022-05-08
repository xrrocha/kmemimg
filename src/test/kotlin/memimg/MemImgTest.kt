package memimg

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MemImgTest {

    companion object {
        init {
            initLogger()
        }
    }

    @Test
    fun runsWithMemoryEventSourcing() {
        doTest(InMemoryBankEventSourcing)
    }

    @Test
    fun runsWithJsonFileEventSourcing() {
        val file = File.createTempFile("bank", ".json")
        doTest(JsonFileBankEventSourcing(file))
        println(file.readText())
    }

    private fun doTest(eventSourcing: EventSourcing<Command<Bank>>) {

        val bank1 = Bank()
        val memimg1 = MemImg(bank1, eventSourcing)
        memimg1.execute(CreateAccount("janet", "Janet Doe"))
        assertEquals(Amount.ZERO, bank1.accounts["janet"]!!.balance)

        memimg1.execute(Deposit("janet", Amount("100")))
        assertEquals(Amount("100"), bank1.accounts["janet"]!!.balance)

        memimg1.execute(Withdrawal("janet", Amount("10")))
        assertEquals(Amount("90"), bank1.accounts["janet"]!!.balance)

        memimg1.execute(CreateAccount("john", "John Doe"))
        assertEquals(Amount.ZERO, bank1.accounts["john"]!!.balance)

        memimg1.execute(Deposit("john", Amount("50")))
        assertEquals(Amount("50"), bank1.accounts["john"]!!.balance)

        memimg1.execute(Transfer("janet", "john", Amount(20)))
        assertEquals(Amount("70"), bank1.accounts["janet"]!!.balance)
        assertEquals(Amount("70"), bank1.accounts["john"]!!.balance)

        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }

        val bank2 = Bank()
        val memimg2 = MemImg(bank2, eventSourcing)
        // Look ma: system state restored from empty initial state and event sourcing!
        assertEquals(Amount("70"), bank2.accounts["janet"]!!.balance)
        assertEquals(Amount("70"), bank2.accounts["john"]!!.balance)

        // Some random query; executes at in-memory speeds
        val accountsWith70 = memimg2.execute(object : Query<Bank> {
            override fun execute(system: Bank) =
                system.accounts.values
                    .filter { it.balance == Amount("70") }
                    .map { it.name }
                    .toSet()
        })
        assertEquals(setOf("Janet Doe", "John Doe"), accountsWith70)

        // Attempt to transfer beyond means...
        val insufficientFunds = assertThrows<Exception> {
            memimg2.execute(Transfer("janet", "john", Amount("1000")))
        }
        assertContains(insufficientFunds.message!!, "Can't have negative balance")
        // Look ma: system state restored on failure after partial mutation
        assertEquals(Amount("70"), bank2.accounts["janet"]!!.balance)
        assertEquals(Amount("70"), bank2.accounts["john"]!!.balance)

        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }
    }
}
