package memimg

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

interface EventSourcing<E> {

    fun replay(consume: (E) -> Unit) = asSequence().forEach(consume)

    fun append(event: E)
    fun asSequence(): Sequence<E>
}

interface Converter<T> {
    fun parse(string: String): T
    fun format(value: T): String
}

open class FileEventSourcing<E>(private val file: File, private val converter: Converter<E>) : EventSourcing<E> {

    private val out: PrintWriter

    init {
        require(
            (file.exists() && file.canRead() && file.canWrite()) ||
                    (!file.exists() &&
                            (file.parentFile.canWrite() || file.parentFile.mkdirs()))
        ) {
            "Inaccessible file ${file.absolutePath}"
        }
        out = PrintWriter(FileWriter(file, true), true)
    }

    override fun asSequence(): Sequence<E> = file.bufferedReader().lineSequence().map(converter::parse)

    override fun append(event: E) = out.println(converter.format(event).replace("\n", " "))
}
