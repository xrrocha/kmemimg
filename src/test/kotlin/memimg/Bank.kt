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
import java.math.BigDecimal

/* 1) Domain entities: Bank and Account */
typealias Amount = BigDecimal

data class Bank(val accounts: MutableMap<String, Account> = HashMap())

data class Account(val id: String, val name: String) {
    var balance: Amount by TxDelegate(Amount.ZERO) { it >= Amount.ZERO }
}

/* Application commands: Deposit, Withdrawal, Transfer */
interface BankCommand : Command {
    fun execute(bank: Bank)
    override fun execute(system: Any) = execute(system as Bank)
}

interface BankQuery : Query {
    fun execute(bank: Bank): Any?
    override fun execute(system: Any) = execute(system as Bank)
}

@Serializable
data class CreateAccount(val id: String, val name: String) : BankCommand {
    override fun execute(bank: Bank) {
        bank.accounts[id] = Account(id, name)
    }
}

@Serializable
data class Deposit(val accountId: String, @Contextual val amount: Amount) : BankCommand {
    override fun execute(bank: Bank) {
        bank.accounts[accountId]!!.balance += amount
    }
}

@Serializable
data class Withdrawal(val accountId: String, @Contextual val amount: Amount) : BankCommand {
    override fun execute(bank: Bank) {
        bank.accounts[accountId]!!.balance -= amount
    }
}

@Serializable
data class Transfer(val fromAccountId: String, val toAccountId: String, @Contextual val amount: Amount) : BankCommand {
    override fun execute(bank: Bank) {
        // Operation order deliberately as to exercise rollback...
        Deposit(toAccountId, amount).execute(bank)
        Withdrawal(fromAccountId, amount).execute(bank)
    }
}

/* 3) Serialization infrastructure: JSON, line-oriented */
val bankJsonFormat = Json {
    serializersModule = SerializersModule {
        polymorphic(BankCommand::class) {
            subclass(CreateAccount::class)
            subclass(Deposit::class)
            subclass(Withdrawal::class)
            subclass(Transfer::class)
        }
        contextual(BigDecimal::class, AmountSerializer)
    }
}

object BankJsonConverter : LineConverter<BankCommand> {
    override fun parse(string: String): BankCommand = bankJsonFormat.decodeFromString(string)

    // Ensure JSON content lies on a single line!
    override fun format(value: BankCommand): String = bankJsonFormat.encodeToString(value).replace("\n", " ")
}

object AmountSerializer : KSerializer<BigDecimal> {
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toString())
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
}
