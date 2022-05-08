# Memory Image in Kotlin

![Memory Image](kmemimg.png)

A simple Kotlin implementation of the
[Memory Image](https://www.martinfowler.com/bliki/MemoryImage.html)
architectural pattern:

```kotlin
class MemImg(private val system: Any, 
             private val eventSourcing: EventSourcing) {

    init {
        synchronized(this) {
            eventSourcing.replay<Command> { 
                command -> command.applyTo(system) 
            }
        }
    }

    // Single-threaded, lightning-fast
    fun execute(command: Command): Unit = synchronized(this) {
        TxManager.begin()
        try {
            command.applyTo(system)
            eventSourcing.append(command)
        } catch (e: Exception) {
            TxManager.rollback()
            logger.severe("Error in command: ${e.message}")
            throw e
        }
    }

    fun execute(query: Query): Any? = query.extractFrom(system)
}
```

## A Simple Example: Bank Accounts

![Bank](bank.png)

```kotlin
class MemImgTest {
    
    /* 1) Domain entities: Bank and Account */
    typealias Amount = BigDecimal
    
    data class Bank(val accounts: MutableMap<String, Account> = HashMap())
    
    data class Account(val id: String, val name: String) {
        // Mutable properties participate in transactions
        var balance: Amount by TxDelegate(Amount.ZERO) { it >= Amount.ZERO }
    }
    
    /* 2) Application commands: CreateAccount, Deposit, Withdrawal, Transfer */
    interface BankCommand : Command {
        fun applyTo(bank: Bank)
        override fun applyTo(system: Any) = executeOn(system as Bank)
    }
    interface BankQuery : Query {
        fun extractFrom(bank: Bank): Any?
        override fun extractFrom(system: Any) = executeOn(system as Bank)
    }
    interface AccountCommand : BankCommand {
        val accountId: String
        fun applyTo(account: Account)
        override fun applyTo(bank: Bank) {
            applyTo(bank.accounts[accountId]!!)
        }
    }
    
    data class CreateAccount(val id: String, val name: String) : BankCommand {
        override fun applyTo(bank: Bank) {
            bank.accounts[id] = Account(id, name)
        }
    }    
    data class Deposit(override val accountId: String, val amount: Amount) : AccountCommand {
        override fun applyTo(account: Account) {
            account.balance += amount
        }
    }
    data class Withdrawal(override val accountId: String,val amount: Amount) : AccountCommand {
        override fun applyTo(account: Account) {
            account.balance -= amount
        }
    }
    data class Transfer(val fromAccountId: String, val toAccountId: String, val amount: Amount) : BankCommand {
        override fun applyTo(bank: Bank) {
            // Operation order deliberately set so as to benefit from rollback...
            Deposit(toAccountId, amount).applyTo(bank)
            Withdrawal(fromAccountId, amount).applyTo(bank)
        }
    }

    // In-memory, volatile, non-persistent event sourcing
    // Check implementation for the (JSON File) real thing™
    object BankEventSourcing : EventSourcing {
        private val buffer = mutableListOf<Any>()
        override fun <E> replay(eventConsumer: (E) -> Unit) = buffer.forEach{eventConsumer(it as E)}
        override fun append(event: Any) { buffer += event }
    }

    @Test
    fun `builds and restores domain model state` () {
        val bank1 = Bank()
        val memimg1 = MemImg(bank1, BankEventSourcing)
        
        memimg1.execute(CreateAccount("janet", "Janet Doe"))
        assertEquals(Amount.ZERO, bank1.accounts["janet"]!!.balance)

        memimg1.execute(Deposit("janet", Amount(100)))
        assertEquals(Amount(100), bank1.accounts["janet"]!!.balance)

        memimg1.execute(Withdrawal("janet", Amount(10)))
        assertEquals(Amount(90), bank1.accounts["janet"]!!.balance)

        memimg1.execute(CreateAccount("john", "John Doe"))
        assertEquals(Amount.ZERO, bank1.accounts["john"]!!.balance)

        memimg1.execute(Deposit("john", Amount(50)))
        assertEquals(Amount(50), bank1.accounts["john"]!!.balance)

        memimg1.execute(Transfer("janet", "john", Amount(20)))
        assertEquals(Amount(70), bank1.accounts["janet"]!!.balance)
        assertEquals(Amount(70), bank1.accounts["john"]!!.balance)

        memimg1.close()

        val bank2 = Bank()
        val memimg2 = MemImg(bank2, BankEventSourcing)
        // Look ma: system state restored from empty initial state via event sourcing!
        assertEquals(Amount(70), bank2.accounts["janet"]!!.balance)
        assertEquals(Amount(70), bank2.accounts["john"]!!.balance)

        // Some random query; executes at in-memory speeds
        val accountsWith70 = memimg2.execute(object : BankQuery {
            override fun executeOn(bank: Bank) =
                bank.accounts.values
                    .filter { it.balance == Amount(70) }
                    .map { it.name }
                    .toSet()
        })
        assertEquals(setOf("Janet Doe", "John Doe"), accountsWith70)

        // Attempt to transfer beyond means...
        val insufficientFunds = assertThrows<Exception> {
            memimg2.execute(Transfer("janet", "john", Amount(1000)))
        }
        assertContains(insufficientFunds.message!!, "Invalid value for Account.balance")
        // Look ma: system state restored on failure after partial mutation
        assertEquals(Amount(70), bank2.accounts["janet"]!!.balance)
        assertEquals(Amount(70), bank2.accounts["john"]!!.balance)

        memimg2.close()
    }
}
```