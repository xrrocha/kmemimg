package memimg

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

class TxDelegate<T>(initialValue: T, private val isValid: (T) -> Boolean) {
    private var value: T
    private val setter: (T) -> Unit = { value -> this.value = value }

    init {
        require(isValid(initialValue)) { "Invalid initial value: $initialValue" }
        value = initialValue
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        require(isValid(value)) { "Invalid value for ${thisRef::class.simpleName}.${property.name}: $value" }
        TxManager.remember(thisRef, property.name, this.value, setter)
        setter(value)
    }
}
