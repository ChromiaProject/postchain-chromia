package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.*
import net.postchain.chain0.common.queries.getProviderData
import net.postchain.client.core.PostchainClient
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvDictionary
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption
import java.time.Instant
import java.util.*

class CommandGetProposal : CliktCommand(
    name = "info",
    help = "Gets information of a given proposal"
) {
    private val config by configOption()

    private val idx by proposalIndexOption().required()

    private val verbose by option("-v", "--verbose", help = "Show proposal content").flag()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val proposal = client.getProposal(idx)
        val proposedBy = client.getProviderData(proposal.proposedBy)
        println("""
            Proposal: $idx - ${proposal.proposalType.name}
            Proposed by ${proposedBy.name} - ${proposedBy.pubkey.toHex()}
            Time: ${Date.from(Instant.ofEpochMilli(proposal.timestamp))}
        """.trimIndent())
        if (verbose) println(formatProposal(client, proposal))
    }
    
    fun formatProposal(client: PostchainClient, proposal: GetProposalResult): String {
        return when (proposal.proposalType) {
            ProposalType.bc ->  {
                val p = client.getBlockchainProposal(proposal.rowid) ?: return ""
                """
                    Container: ${p.container}
                    Data: ${p.data.toHex()}
                """.trimIndent()
            }
            ProposalType.conf -> {
                val p = client.getConfigurationProposal(proposal.rowid) ?: return ""
                val currentConf = GtvDecoder.decodeGtv(p.currentConf.data) as GtvDictionary
                val newConf = GtvDecoder.decodeGtv(p.proposedConf.data) as GtvDictionary
                val diff = mutableMapOf<String, Pair<Gtv?, Gtv?>>()
                val new = mutableMapOf<String, Gtv>()
                newConf.dict.forEach { (t, u) ->
                    val current = currentConf[t]
                    if (current == null) {
                        new[t] = u
                    } else if (u != current) {
                        diff[t] = current to u
                    }
                }
                val removed = currentConf.dict.filterKeys { !newConf.dict.containsKey(it) }

                """
                    Enabled at height: ${p.proposedConf.height}
                    
                    New tags
                    ------------------------------------
                    ${new.entries.joinToString("\n") { (k, v) -> "$k: $v" }}
                    
                    Removed tags
                    -----------------------------------
                    ${removed.entries.joinToString("\n") { (k, v) -> "$k: $v" }}
                    
                    Changed tags
                    ----------------------------------
                    ${diff.entries.joinToString("\n") { (k, v) -> "$k: \nfrom - ${v.first}\nto - ${v.second}" }}
                """.trimIndent()
            }

            else -> ""
        }
    }
}
