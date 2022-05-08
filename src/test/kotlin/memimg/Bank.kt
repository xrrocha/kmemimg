package memimg

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

typealias Amount = BigDecimal

// TODO Remove serialization annotations from domain classes!
@Serializable
data class Bank(val accounts: MutableMap<String, Account> = HashMap())

@Serializable
data class Account(val id: String, val name: String) {
    var balance: Amount by TxDelegate(Amount.ZERO) { it >= Amount.ZERO }
}

interface BankCommand : Command<Bank>

@Serializable
data class CreateAccount(val id: String, val name: String) : BankCommand {
    override fun execute(system: Bank) {
        system.accounts[id] = Account(id, name)
    }
}

@Serializable
sealed class AccountCommand(private val accountId: String) : BankCommand {
    abstract fun execute(account: Account)
    final override fun execute(system: Bank) {
        execute(system.accounts[accountId]!!)
    }
}

@Serializable
data class Deposit(
    val accountId: String,
    @Serializable(with = AmountSerializer::class) val amount: Amount
) :
    BankCommand {
    override fun execute(system: Bank) {
        system.accounts[accountId]!!.balance += amount
    }
}

@Serializable
data class Withdrawal(
    val accountId: String,
    @Serializable(with = AmountSerializer::class) val amount: Amount
) :
    BankCommand {
    override fun execute(system: Bank) {
        system.accounts[accountId]!!.balance -= amount
    }
}

@Serializable
data class Transfer(
    val fromAccountId: String,
    val toAccountId: String,
    @Serializable(with = AmountSerializer::class) val amount: Amount
) : BankCommand {
    override fun execute(system: Bank) {
        // Operation order deliberately set to require rollback!
        Deposit(toAccountId, amount).execute(system)
        Withdrawal(fromAccountId, amount).execute(system)
    }
}

// Serialization infrastructure
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
    override fun format(value: Command<Bank>): String = bankJsonFormat.encodeToString(value).replace("\n", " ")
}

object AmountSerializer : KSerializer<BigDecimal> {
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toString())
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
}
