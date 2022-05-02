package memimg

import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.concurrent.getOrSet

interface Command<S> {
    fun execute(system: S)
}

interface Query<S> {
    fun execute(system: S): Any?
}

interface EventSourcing<S> {
    fun allCommands(): Iterable<Command<S>>
    fun append(command: Command<S>)
}

object TxManager {
    private val logger = Logger.getLogger("UndoLog")
    private val journal = ThreadLocal<MutableMap<Pair<Any, String>, () -> Unit>>()

    fun begin() {
        journal.getOrSet { mutableMapOf() }.clear()
    }

    fun <T> remember(who: Any, what: String, value: T, undo: (T) -> Unit) {
        journal.get().computeIfAbsent(Pair(who, what)) { { undo(value) } }
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

class MemImg<S>(
    private val system: S,
    private val eventSourcing: EventSourcing<S>
) {

    init {
        eventSourcing.allCommands().forEach { command -> command.execute(system) }
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
        private var logger: Logger

        init {
            LogManager.getLogManager().readConfiguration(openResource("logging.properties"))
            logger = Logger.getLogger("MemoryImage")
        }

        private fun openResource(resourceName: String) =
            this::class.java.classLoader.getResourceAsStream(resourceName)
                ?: throw java.lang.IllegalArgumentException("No such resource: $resourceName")
    }
}

