package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import de.m3y.kformat.table
import net.postchain.chain0.common.proposal.*
import net.postchain.chain0.common.proposal.voter_set.getVoterSetUpdateProposal
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
        val proposal = client.getProposal(idx) ?: return println("Proposal $idx not found")
        val proposedBy = client.getProviderData(proposal.proposedBy)
        println("""
            Proposal: $idx - ${proposal.type.name}
            Proposed by ${proposedBy.name} - ${proposedBy.pubkey.toHex()}
            Time: ${Date.from(Instant.ofEpochMilli(proposal.timestamp))}
        """.trimIndent())
        if (verbose) println(formatProposal(client, proposal))
    }
    
    fun formatProposal(client: PostchainClient, proposal: GetProposalResult): String {
        return when (proposal.type) {
            ProposalType.bc ->  {
                val p = client.getBlockchainProposal(proposal.id) ?: return ""
                val conf = GtvDecoder.decodeGtv(p.data)
                "Container: ${p.container}\nData: $conf"
            }
            ProposalType.conf -> {
                val p = client.getConfigurationProposal(proposal.id) ?: return ""
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
            ProposalType.voter_set_update -> {
                val vsu = client.getVoterSetUpdateProposal(proposal.id) ?: return ""
                val t = table {
                    row("Voter set:", vsu.voterSet)
                    row("Governor update", vsu.governor ?: "")
                    row("Majority threshold update", vsu.threshold ?: "")
                    row("New member", vsu.addMember.joinToString(", ") { it.toHex() })
                    row("Remove member", vsu.removeMember.joinToString(", ") { it.toHex() })
                }.render()
                return t.toString()
            }

            else -> ""
        }
    }
}
