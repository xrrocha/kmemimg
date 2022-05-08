package memimg

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File
import java.math.BigDecimal

typealias Amount = BigDecimal

@Serializable
data class Bank(val accounts: MutableMap<String, Account> = HashMap())

@Serializable
data class Account(val id: String, val name: String) {
    @Contextual
    var balance: Amount by TxDelegate(Amount.ZERO) { newBalance ->
        require(newBalance >= Amount.ZERO) { "Can't have negative balance: $newBalance" }
    }
}

@Serializable
data class CreateAccount(val id: String, val name: String) : Command<Bank> {
    override fun execute(system: Bank) {
        system.accounts[id] = Account(id, name)
    }
}

@Serializable
sealed class AccountCommand(private val accountId: String) : Command<Bank> {
    abstract fun execute(account: Account)
    final override fun execute(system: Bank) {
        execute(system.accounts[accountId]!!)
    }
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toString())
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
}

@Serializable
data class Deposit(val depositId: String, @Serializable(with = BigDecimalSerializer::class) val amount: Amount) :
    AccountCommand(depositId) {
    override fun execute(account: Account) {
        account.balance += amount
    }
}

@Serializable
data class Withdrawal(
    val withdrawalId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: Amount
) : AccountCommand(withdrawalId) {
    override fun execute(account: Account) {
        account.balance -= amount
    }
}

@Serializable
data class Transfer(
    val fromAccountId: String,
    val toAccountId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: Amount
) : Command<Bank> {
    override fun execute(system: Bank) {
        // Operation order deliberately set to require rollback!
        Deposit(toAccountId, amount).execute(system)
        Withdrawal(fromAccountId, amount).execute(system)
    }
}

// In-memory, volatile, non-persistent event sourcing
object InMemoryBankEventSourcing : EventSourcing<Command<Bank>> {
    private val buffer = mutableListOf<Command<Bank>>()
    override fun asSequence(): Sequence<Command<Bank>> = buffer.asSequence()
    override fun append(event: Command<Bank>) {
        buffer += event
    }
}

val bankJsonFormat = Json {
    serializersModule = SerializersModule {

        polymorphic(Command::class) {
            subclass(CreateAccount::class)
            subclass(Deposit::class)
            subclass(Withdrawal::class)
            subclass(Transfer::class)
        }
    }
}

object BankJsonConverter : Converter<Command<Bank>> {
    override fun parse(string: String): Command<Bank> = bankJsonFormat.decodeFromString(string)
    override fun format(value: Command<Bank>): String = bankJsonFormat.encodeToString(value)
}

class BankEventSourcing(file: File) : FileEventSourcing<Command<Bank>>(file, BankJsonConverter)
