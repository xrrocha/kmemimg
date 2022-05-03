package memimg

import java.io.InputStream
import java.util.logging.LogManager

fun main(/*args: Array<String>*/) {
    initLogger()
    // TODO Add CLI runner
}

fun initLogger() {
    LogManager.getLogManager().readConfiguration(openResource("logging.properties"))
}

fun openResource(resourceName: String): InputStream =
    MemImg::class.java.classLoader.getResourceAsStream(resourceName)
        ?: throw java.lang.IllegalArgumentException("No such resource: $resourceName")
