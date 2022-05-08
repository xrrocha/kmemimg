package memimg

import java.util.logging.Logger

interface Command {
    fun executeOn(system: Any)
}

interface Query {
    fun executeOn(system: Any): Any?
}

class MemImg(private val system: Any, private val eventSourcing: EventSourcing) : AutoCloseable {

    init {
        synchronized(this) {
            eventSourcing.replay<Command> { e -> e.executeOn(system) }
        }
    }

    fun execute(command: Command): Unit =
        synchronized(this) {
            TxManager.begin()
            try {
                command.executeOn(system)
                eventSourcing.append(command)
            } catch (e: Exception) {
                TxManager.rollback()
                logger.severe("Error executing command: ${e.message ?: e.toString()}")
                throw e
            }
        }

    fun execute(query: Query): Any? = query.executeOn(system)

    override fun close() {
        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }
    }

    companion object {
        private var logger = Logger.getLogger("MemoryImage")
    }
}

