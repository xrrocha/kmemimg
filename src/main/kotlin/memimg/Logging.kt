package memimg

import java.io.FileNotFoundException
import java.io.InputStream
import java.util.logging.LogManager

fun initLogger() {
    LogManager.getLogManager().readConfiguration(openResource("logging.properties"))
}

fun openResource(resourceName: String): InputStream =
    MemImg::class.java.classLoader.getResourceAsStream(resourceName)
        ?: throw FileNotFoundException("No such resource: $resourceName")
