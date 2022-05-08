package memimg

import java.util.logging.Logger
import kotlin.concurrent.getOrSet
import kotlin.reflect.KProperty

object TxManager {
    private val logger = Logger.getLogger("TxManager")
    private val journal = ThreadLocal<MutableMap<Pair<Any, String>, () -> Unit>>()

    fun begin() {
        journal.getOrSet { mutableMapOf() }.clear()
    }

    fun <T> remember(who: Any, what: String, value: T, undo: (T) -> Unit) {
        journal.get().computeIfAbsent(Pair(who, what)) { { undo(value) } }
    }

    fun rollback() {
        journal.get().forEach { entry ->
            val (whoWhat, undo) = entry
            try {
                undo.invoke()
            } catch (e: Exception) {
                val (who, what) = whoWhat
                logger.warning("Error retracting ${who::class.simpleName}.$what: ${e.message ?: e.toString()}")
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
        require(isValid(value)) { "Invalid value for ${property.name}: $value" }
        TxManager.remember(thisRef, property.name, this.value, setter)
        setter(value)
    }
}
