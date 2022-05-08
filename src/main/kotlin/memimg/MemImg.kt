package memimg

import java.util.logging.Logger

interface Command {
    fun applyTo(system: Any)
}

interface Query {
    fun extractFrom(system: Any): Any?
}

class MemImg(private val system: Any, private val eventSourcing: EventSourcing) : AutoCloseable {

    init {
        synchronized(this) {
            eventSourcing.replay<Command> { command -> command.applyTo(system) }
        }
    }

    fun execute(command: Command): Unit =
        synchronized(this) {
            TxManager.begin()
            try {
                command.applyTo(system)
                eventSourcing.append(command)
            } catch (e: Exception) {
                TxManager.rollback()
                logger.severe("Error executing command: ${e.message ?: e.toString()}")
                throw e
            }
        }

    fun execute(query: Query): Any? = query.extractFrom(system)

    override fun close() {
        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }
    }

    companion object {
        private var logger = Logger.getLogger("MemoryImage")
    }
}

