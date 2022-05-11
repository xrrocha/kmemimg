package memimg

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.logging.LogManager

fun initLogger() {
    val resourceName = "logging.properties"
    openResource(resourceName)
        .map(LogManager.getLogManager()::readConfiguration)
        .getOrElse { throw IllegalStateException("Can't find resource $resourceName") }
}

fun openResource(resourceName: String): Either<FileNotFoundException, InputStream> =
    MemImgProcessor::class.java.classLoader.getResourceAsStream(resourceName)?.right()
        ?: FileNotFoundException("No such resource: $resourceName").left()
