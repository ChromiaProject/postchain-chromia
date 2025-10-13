package net.postchain.images.common

import mu.KLogger
import mu.KotlinLogging
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Utility class for programmatically configuring Log4j2 appenders and loggers. We still use the log4j2-test.yml file
 * for main logger (test and mounted in nodes)
 */
object LoggingConfig {
    private val loggerContext = LogManager.getContext(false) as LoggerContext
    private val configuration = loggerContext.configuration
    private val forkIdSuffix =
            if (System.getProperty("forkId") == null) ""
            else "-" + System.getProperty("forkId", "0")

    private fun createFileAppender(name: String, fileName: String, pattern: String): FileAppender {
        val file = File(fileName)
        file.parentFile?.mkdirs()

        val layout = PatternLayout.newBuilder()
            .withPattern(pattern)
            .withCharset(StandardCharsets.UTF_8)
            .build()

        return FileAppender.newBuilder()
            .setName(name)
            .setLayout(layout)
            .withFileName(fileName)
            .withCreateOnDemand(true)
            .withAppend(false)
            .build()
    }

    fun createLogger(logDir: String, name: String): KLogger {
        val loggerName = "${logDir}_${name}Logger"
        val appenderName = "${logDir}_${name}Appender"
        val fileName = "logs/${logDir}/${name}${forkIdSuffix}.log"

        if (configuration.getAppender<FileAppender>(appenderName) == null) {
            val appender = createFileAppender(appenderName, fileName, "%msg%n")
            appender.start()
            configuration.addAppender(appender)

            val loggerConfig = LoggerConfig(loggerName, Level.INFO, false)
            loggerConfig.addAppender(appender, Level.INFO, null)
            configuration.addLogger(loggerName, loggerConfig)

            loggerContext.updateLoggers()
        }

        return KotlinLogging.logger(loggerName)
    }
}