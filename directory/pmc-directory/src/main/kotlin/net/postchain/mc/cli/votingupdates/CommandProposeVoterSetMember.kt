package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeVoterSetMember : CliktCommand(
    name = "update",
    help = "proposes an update of a voter set's members. add = false => remove provider from vote set. Voter set's governor has authority to update members"
) {
    private val nodeConfig by nodeConfigOption()

    private val provider by requiredPubkeyOption()

    private val vsName by nameOption("Name of voter set").required()

    private val add by option("-a", "--add", help = "Add or remove provider pubkey from voter set")
        .flag("-r", "--remove", default = true)

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeVoterSetMember(vsName, provider, add)
        println("proposal for member update of voter set $vsName has been added successfully")
    }
}