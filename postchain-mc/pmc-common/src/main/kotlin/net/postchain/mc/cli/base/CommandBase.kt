package net.postchain.mc.cli.base

import kotlin.random.Random

const val NAME_LENGTH = 10
const val NAME_LENGTH_MAX = 50

object CommandBase {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun autoGenerateName(): String {
        return (1..NAME_LENGTH)
                .map { Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
    }

    fun isAlphanumeric(string: String): Boolean {
        val regex = "^[a-zA-Z0-9]*$"
        return string.matches(regex.toRegex())
    }

}