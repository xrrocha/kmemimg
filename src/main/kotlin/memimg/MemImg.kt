package memimg

import java.util.logging.Logger

interface Command {
    fun applyTo(system: Any)
}

interface Query {
    fun extractFrom(system: Any): Any?
}

class MemoryImageProcessor(private val system: Any, private val eventSourcing: EventSourcing) : AutoCloseable {
    init {
        // Replay previously serialized events so as to restore in-memory system state
        synchronized(this) { // Single-threaded
            // Any failure is propagated and memory image processor is not instantiated
            eventSourcing.replay<Command> { command -> command.applyTo(system) }
        }
    }

    fun execute(command: Command): Unit = synchronized(this) { // Single-threaded
        TxManager.begin()
        try {
            command.applyTo(system) // Try and apply command
            try {
                eventSourcing.append(command) // Serialize; retry internally if needed
            } catch (e: Exception) {
                // Note: no attempt to rollback: this is irrecoverable
                logger.severe("Error persisting command: ${e.message ?: e.toString()}")
                // Give up: no further processing; start over when serialization is restored
                throw e
            }
        } catch (e: Exception) {
            TxManager.rollback() // Undo any partial mutation
            val errorMessage = "Error executing command: ${e.message ?: e.toString()}"
            // It's (kinda) ok for a command to fail
            // Re-throw as «CommandApplicationException» and go on
            logger.warning(errorMessage)
            throw CommandApplicationException(errorMessage, e)
        }
    }

    fun execute(query: Query): Any? = query.extractFrom(system) // Can be multi-threaded

    override fun close() {
        if (eventSourcing is AutoCloseable) {
            eventSourcing.close()
        }
    }

    companion object {
        private var logger = Logger.getLogger("MemoryImage")
    }
}

// This exception signals the a *recoverable*, command-related error conditions
class CommandApplicationException(message: String, cause: Exception): Exception(message, cause) {
    constructor(cause: Exception): this(cause.message ?: cause.toString(), cause)
}
