package memimg

import java.util.logging.Logger

interface Command<S> {
    fun execute(system: S)
}

interface Query<S> {
    fun execute(system: S): Any?
}

class MemImg<S>(private val system: S, private val eventSourcing: EventSourcing): AutoCloseable {

    init {
        synchronized(this) {
            eventSourcing.replay<Command<S>> { e -> e.execute(system) }
        }
    }

    fun execute(command: Command<S>): Unit =
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

    fun execute(query: Query<S>): Any? = query.execute(system)
    override fun close() {
        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }
    }

    companion object {
        private var logger = Logger.getLogger("MemoryImage")
    }
}

