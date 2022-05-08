package memimg

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

interface EventSourcing<E> {

    fun replay(consume: (E) -> Unit) = asSequence().forEach(consume)

    fun append(event: E)
    fun asSequence(): Sequence<E>
}

abstract class FileEventSourcing<E>(private val file: File) : EventSourcing<E> {

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

    protected abstract fun parse(string: String): E
    protected abstract fun format(command: E): String

    override fun asSequence(): Sequence<E> = file.bufferedReader().lineSequence().map(this::parse)

    override fun append(event: E) = out.println(format(event).replace("\n", " "))
}
