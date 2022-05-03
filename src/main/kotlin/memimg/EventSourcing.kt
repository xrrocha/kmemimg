package memimg

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

abstract class LineFileEventSourcing<S>(private val file: File) : EventSourcing<S> {

    private val out: PrintWriter

    init {
        require(
            (!file.exists() && file.parentFile.canWrite()) ||
                    (file.exists() && file.canRead() && file.canWrite())
        ) {
            "Inaccessible file ${file.absolutePath}"
        }
        out = PrintWriter(FileWriter(file, true), true)
    }

    protected abstract fun parse(string: String): Command<S>
    protected abstract fun format(command: Command<S>): String

    override fun allCommands(): Sequence<Command<S>> = file.bufferedReader().lineSequence().map(this::parse)

    override fun append(command: Command<S>) = out.println(format(command).replace("\n", " "))
}
