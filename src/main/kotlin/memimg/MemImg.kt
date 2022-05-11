package memimg

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.logging.Logger

interface Command {
    fun applyTo(system: Any): Any?
}

interface Query {
    fun extractFrom(system: Any): Any?
}

class MemImgProcessor(private val system: Any, private val eventStorage: EventStorage) : AutoCloseable {

    init {
        synchronized(system) { eventStorage.replay<Command>(::execute) }
    }

    fun execute(query: Query): Either<FailureOutcome, Any?> =
        Either.catch { query.extractFrom(system) }
            .mapLeft { CommandFailure(it, "executing query", query) }
            .tapLeft { logger.finer(it.errorMessage) }

    fun execute(command: Command): Either<FailureOutcome, Any?> =
        synchronized(this) {
            try {
                TxManager.begin()
                Either.catch { command.applyTo(system) }
                    .mapLeft { CommandFailure(it, "executing command", command) }
                    .tapLeft {
                        logger.finer(it.errorMessage)
                        TxManager.rollback()
                    }
                    .flatMap { result ->
                        Either.catch { eventStorage.append(command) }
                            .mapLeft { SystemFailure(it, "serializing command", command) }
                            .tapLeft { logger.severe(it.errorMessage) }
                            .map { result }
                    }
            } catch (t: Throwable) {
                val errorMessage = t.message ?: t.toString()
                logger.severe("Error executing command: $errorMessage")
                SystemFailure(t, "managing transaction ($errorMessage)", command).left()
            }
        }

    abstract class FailureOutcome(val throwable: Throwable, context: String, source: Any) {
        val errorMessage = "Error while $context ${source::class.simpleName}: ${throwable.message}"

        override fun toString(): String = errorMessage
    }

    class CommandFailure(throwable: Throwable, context: String, source: Any) :
        FailureOutcome(throwable, context, source)

    class SystemFailure(throwable: Throwable, context: String, source: Any) : FailureOutcome(throwable, context, source)

    override fun close() {
        if (eventStorage is AutoCloseable) {
            eventStorage.close()
        }
    }

    companion object {
        private var logger = Logger.getLogger(MemImgProcessor::class.qualifiedName)
    }
}
