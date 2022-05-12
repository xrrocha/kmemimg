package memimg

import arrow.core.Either
import arrow.core.left
import arrow.core.right

interface Validator<out T> {
    fun validate(value: @UnsafeVariance T?): Either<String, Unit>
}

open class RegexValidator(private val regex: Regex, private val message: String): Validator<String> {
    constructor(pattern: String, messageFormat: String): this(pattern.toRegex(), messageFormat)
    override fun validate(value: String?): Either<String, Unit> =
        when {
            value == null || regex.matchEntire(value) != null -> Unit.right()
            else -> "$message: $value".left()
        }
}
