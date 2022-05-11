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

/* 2) Application commands: Deposit, Withdrawal, Transfer */
interface BankCommand : Command {
    fun applyTo(bank: Bank)
    override fun applyTo(system: Any) = applyTo(system as Bank)
}

interface BankQuery : Query {
    fun extractFrom(bank: Bank): Any?
    override fun extractFrom(system: Any) = extractFrom(system as Bank)
}

interface AccountCommand : BankCommand {
    val accountId: String
    fun executeOn(account: Account)
    override fun applyTo(bank: Bank) {
        executeOn(bank.accounts[accountId]!!)
    }
}

@Serializable
data class CreateAccount(val id: String, val name: String) : BankCommand {
    override fun applyTo(bank: Bank) {
        bank.accounts[id] = Account(id, name)
    }
}

@Serializable
data class Deposit(override val accountId: String, @Contextual val amount: Amount) : AccountCommand {
    override fun executeOn(account: Account) {
        account.balance += amount
    }
}

@Serializable
data class Withdrawal(override val accountId: String, @Contextual val amount: Amount) : AccountCommand {
    override fun executeOn(account: Account) {
        account.balance -= amount
    }
}

@Serializable
data class Transfer(val fromAccountId: String, val toAccountId: String, @Contextual val amount: Amount) :
    BankCommand {
    override fun applyTo(bank: Bank) {
        // Operation order deliberately set so as to exercise rollback...
        Deposit(toAccountId, amount).applyTo(bank)
        Withdrawal(fromAccountId, amount).applyTo(bank)
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

object BankJsonConverter : TextConverter<BankCommand> {
    override fun parse(string: String): BankCommand = bankJsonFormat.decodeFromString(string)

    // Ensure JSON content lies on a single line!
    override fun format(value: BankCommand): String = bankJsonFormat.encodeToString(value).replace("\n", " ")
}

object AmountSerializer : KSerializer<BigDecimal> {
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toString())
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
}
