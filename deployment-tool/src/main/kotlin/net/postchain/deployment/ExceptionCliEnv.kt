package net.postchain.deployment

import net.postchain.common.exception.UserMistake
import net.postchain.rell.utils.RellCliEnv

class ExceptionCliEnv : RellCliEnv() {
    private val errorMessageBuilder = StringBuilder()

    override fun exit(status: Int): Nothing {
        val errorMessage = errorMessageBuilder.toString()
        errorMessageBuilder.clear()
        throw UserMistake(errorMessage.ifEmpty { "Rell compilation exited with status code $status" })
    }

    override fun print(msg: String, err: Boolean) {
        if (err) {
            errorMessageBuilder.appendLine(msg)
        } else {
            println(msg)
        }
    }
}
