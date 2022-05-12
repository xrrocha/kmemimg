package memimg

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import java.util.logging.Logger
import kotlin.reflect.KProperty

interface TxManager {

    fun begin()
    fun <T> remember(who: Any, what: String, value: T, undo: (T) -> Unit)
    fun rollback()

    companion object : TxManager {
        private val logger = Logger.getLogger("TxManager")
        internal val journal = ThreadLocal<MutableMap<Pair<Any, String>, () -> Unit>>().apply {
            set(mutableMapOf())
        }

        override fun begin() = journal.get().clear()

        // Used by mutable properties to participate in transaction
        override fun <T> remember(who: Any, what: String, value: T, undo: (T) -> Unit) {
            journal.get().computeIfAbsent(Pair(who, what)) { { undo(value) } }
        }

        override fun rollback() =
            journal.get().forEach { entry ->
                val (whoWhat, undo) = entry
                try {
                    undo.invoke()
                } catch (e: Exception) {
                    val (who, what) = whoWhat
                    logger.warning("Error retracting ${who::class.simpleName}.$what: ${e.message ?: e.toString()}")
                    throw e
                }
            }
    }
}

class TxDelegate<T>(initialValue: T, private val validator: Validator<T>? = null) {
    private var value: T
    private val setter: (T) -> Unit = { value -> this.value = value }

    constructor(initialValue: T, validation: (T) -> Boolean) : this(initialValue, object : Validator<T> {
        override fun validate(value: T?): Either<String, Unit> =
            when {
                value == null || validation(value) -> Unit.right()
                else -> "Invalid value: $value".left()
            }
    })

    init {
        validator?.validate(initialValue)?.getOrHandle { throw IllegalArgumentException(it) }
        value = initialValue
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        validator?.validate(value)?.getOrHandle { throw IllegalArgumentException("${property.name}: $it") }
        TxManager.remember(thisRef, property.name, this.value, setter)
        setter(value)
    }
}
