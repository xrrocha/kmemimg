package memimg

import kotlinx.serialization.Serializable

@Serializable
data class Data(val id: Int, val name: String) : Command {
    override fun applyTo(system: Any): Any? {
        @Suppress("UNCHECKED_CAST")
        val list = system as MutableList<Data>
        list += this
        return null
    }
}

object DataParser : TextConverter<Data> {
    override fun parse(string: String): Data =
        with(string.split("\\t".toRegex())) {
            Data(this[0].toInt(), this[1])
        }

    override fun format(value: Data): String =
        "${value.id}\t${value.name}"
}
