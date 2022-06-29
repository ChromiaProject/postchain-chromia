package net.postchain.mc.cli.cluster.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.OptionTransformContext
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.mc.cli.base.CommandBase

fun CliktCommand.clusterNameOption() = option("-n", "--name", help = "Name of cluster")

fun validateAlphaNumeric(): OptionTransformContext.(String) -> Unit =
    {
        require(CommandBase.isAlphanumeric(it)) { "Name must be alphanumeric" }
        require(it.length <= CommandBase.NAME_LENGTH_MAX) { "Name is too long, maximum allowed length is ${CommandBase.NAME_LENGTH_MAX}" }
    }


