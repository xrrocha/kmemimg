package memimg

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

interface EventStorage {
    fun <E> replay(eventConsumer: (E) -> Unit)
    fun append(event: Any)
}

interface LineConverter<T> {
    fun parse(string: String): T
    fun format(value: T): String
}

open class LineFileEventStorage<E : Any, C : LineConverter<E>>(private val file: File, private val converter: C) :
    EventStorage, AutoCloseable {

    private lateinit var out: PrintWriter

    init {
        require(
            (file.exists() && file.canRead() && file.canWrite()) ||
                    (!file.exists() &&
                            (file.parentFile.canWrite() || file.parentFile.mkdirs()))
        ) {
            "Inaccessible file ${file.absolutePath}"
        }
        file.createNewFile()
    }

    override fun <E> replay(eventConsumer: (E) -> Unit) = file.bufferedReader().use { reader ->
        @Suppress("UNCHECKED_CAST")
        reader.lineSequence().map(converter::parse).forEach { eventConsumer(it as E) }
        out = PrintWriter(FileWriter(file, true), true)
    }

    @Suppress("UNCHECKED_CAST")
    override fun append(event: Any) = out.println(converter.format(event as E))

    override fun close() = out.close()
}
