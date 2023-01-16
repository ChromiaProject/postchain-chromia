package net.postchain.mc.cli.util

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.Option
import com.github.ajalt.clikt.sources.ValueSource
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File

object PropertiesConfigurationValueSource {

    fun from(
            filename: String,
            getKey: (Context, Option) -> String = ValueSource.getKey(joinSubcommands = ".")
    ): ValueSource {
        return try {
            val params = Parameters().properties()
                    .setFile(File(filename))
                    .setListDelimiterHandler(DefaultListDelimiterHandler(';'))
            val configuration = FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration

            object : ValueSource {
                override fun getValues(context: Context, option: Option): List<ValueSource.Invocation> {
                    val key = option.valueSourceKey ?: getKey(context, option)
                    val providers = configuration.getStringArray(key)
                    return providers.toList().map { ValueSource.Invocation.value(it) }
                }
            }

        } catch (e: Throwable) {
            object : ValueSource {
                override fun getValues(context: Context, option: Option): List<ValueSource.Invocation> {
                    return emptyList()
                }
            }
        }
    }
}