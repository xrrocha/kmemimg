package memimg

import java.util.logging.Logger

interface Command {
    fun execute(system: Any)
}

interface Query {
    fun execute(system: Any): Any?
}

class MemImg(private val system: Any, private val eventSourcing: EventSourcing) : AutoCloseable {

    init {
        synchronized(this) {
            eventSourcing.replay<Command> { e -> e.execute(system) }
        }
    }

    fun execute(command: Command): Unit =
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

    fun execute(query: Query): Any? = query.execute(system)

    override fun close() {
        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }
    }

    companion object {
        private var logger = Logger.getLogger("MemoryImage")
    }
}

