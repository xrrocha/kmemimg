package memimg

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

interface EventSourcing {
    fun <E> replay(eventConsumer: (E) -> Unit)
    fun append(event: Any)
}

interface Converter<T> {
    fun parse(string: String): T
    fun format(value: T): String
}

open class FileEventSourcing<T: Any, C: Converter<T>>(private val file: File, private val converter: C) :
    EventSourcing, AutoCloseable {

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

    override fun <E> replay(eventConsumer: (E) -> Unit) = file.bufferedReader().use {
        it.lineSequence().map(converter::parse).forEach { eventConsumer(it as E) }
        out = PrintWriter(FileWriter(file, true), true)
    }

    override fun append(event: Any) = out.println(converter.format(event as T))

    override fun close() {
        try {
            out.close()
        } catch (_: Exception) {
        }
    }
}
