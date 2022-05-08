package memimg

import java.util.logging.Logger

interface Command<S> {
    fun execute(system: S)
}

interface Query<S> {
    fun execute(system: S): Any?
}

class MemImg<S>(private val system: S, private val eventSourcing: EventSourcing<Command<S>>) {

    init {
        synchronized(this) {
            eventSourcing.replay { e -> e.execute(system) }
        }
    }

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

