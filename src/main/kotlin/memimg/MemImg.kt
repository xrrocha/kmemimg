package memimg

import java.util.logging.Logger
import kotlin.concurrent.getOrSet
import kotlin.reflect.KMutableProperty0

interface Command<S> {
    fun execute(system: S)
}

interface Query<S> {
    fun execute(system: S): Any?
}

interface EventSourcing<S> {
    fun allCommands(): Sequence<Command<S>>
    fun append(command: Command<S>)
}

object TxManager {
    private val logger = Logger.getLogger("UndoLog")
    private val journal = ThreadLocal<MutableMap<Pair<Any, String>, () -> Unit>>()

    fun begin() {
        journal.getOrSet { mutableMapOf() }.clear()
    }

    fun <T> remember(who: Any, what: String, value: T, undo: (T) -> Unit) {
        journal.getOrSet { mutableMapOf() }.computeIfAbsent(Pair(who, what)) { { undo(value) } }
    }

    fun rollback() {
        val retractions = journal.get()
        if (retractions.isEmpty()) {
            logger.finer("Nothing to undo")
        } else {
            logger.finer("Retracting ${retractions.size} mutations")
        }
        retractions.forEach { entry ->
            val (pair, undo) = entry
            val (who, what) = pair
            try {
                undo.invoke()
            } catch (e: Exception) {
                logger.warning("Error retracting ${who::class.simpleName}.$what: ${e.message ?: e.toString()}")
            }
        }
    }
}

// Convenience mix-in to simplify interaction w/tx manager
interface TxParticipant {
    fun <T> remember(property: KMutableProperty0<T>) {
        TxManager.remember(this, property.name, property.get(), property::set)
    }
}

class MemImg<S>(private val system: S, private val eventSourcing: EventSourcing<S>) {

    init {
        synchronized(this) {
            eventSourcing.allCommands().forEach { command -> command.execute(system) }
        }
    }

    // TODO Make commands available to listeners (e.g. continuous queries)
    fun execute(command: Command<S>) =
        synchronized(this) {
            TxManager.begin()
            try {
                command.execute(system)
                eventSourcing.append(command)
            } catch (e: Exception) {
                TxManager.rollback()
                logger.severe("Error executing command: ${e.message ?: e.toString()}")
                throw e
            }
        }

    fun execute(query: Query<S>) = query.execute(system)

    companion object {
        private var logger = Logger.getLogger("MemoryImage")
    }
}

