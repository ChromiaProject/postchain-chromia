package net.postchain.mc.cli.base

import com.beust.jcommander.Parameter
import mu.KLogging
import net.postchain.mc.config.app.ClientConfig
import net.postchain.mc.config.app.BaseClientConfig
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.random.Random

abstract class CommandBase : Command {

    companion object : KLogging() {
        const val NAME_LENGTH = 10
        const val NAME_LENGTH_MAX = 50
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""

    protected fun loadAppConfig(): ClientConfig {
        try {
            return BaseClientConfig.fromPropertiesFile(config)
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Cannot read config file or not found")
        }
    }

    protected fun autoGenerateName(): String {
        return (1..NAME_LENGTH)
                .map { i -> Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("");
    }

    protected fun isAlphanumeric(string: String): Boolean {
        val regex = "^[a-zA-Z0-9]*$"
        return string.matches(regex.toRegex())
    }
}