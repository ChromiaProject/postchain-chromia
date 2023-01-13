package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.proposal.GetProposalResult
import net.postchain.chain0.common.proposal.ProposalType
import net.postchain.chain0.common.proposal.getBlockchainActionProposal
import net.postchain.chain0.common.proposal.getBlockchainProposal
import net.postchain.chain0.common.proposal.getClusterLimitsProposal
import net.postchain.chain0.common.proposal.getClusterProviderProposal
import net.postchain.chain0.common.proposal.getClusterRemoveProposal
import net.postchain.chain0.common.proposal.getConfigurationProposal
import net.postchain.chain0.common.proposal.getConfigurationProposalAt
import net.postchain.chain0.common.proposal.getContainerLimitsProposal
import net.postchain.chain0.common.proposal.getProposal
import net.postchain.chain0.common.proposal.getProposalVotingResults
import net.postchain.chain0.common.proposal.getProviderQuotaProposal
import net.postchain.chain0.common.proposal.getProviderStateProposal
import net.postchain.chain0.common.proposal.getProvidersBatchProposal
import net.postchain.chain0.common.proposal.getSystemProviderProposal
import net.postchain.chain0.common.proposal.voter_set.getVoterSetUpdateProposal
import net.postchain.chain0.common.queries.getProviderData
import net.postchain.client.core.PostchainClient
import net.postchain.common.toHex
import net.postchain.common.types.RowId
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvDictionary
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.votingupdates.formatThreshold
import net.postchain.mc.gtv.diff.GtvDiffFinder
import java.time.Instant
import java.util.*

class CommandGetProposal : CliktCommand(
        name = "info",
        help = "Gets information of a given proposal"
) {
    private val config by configOption()

    private val id by proposalIndexOption().convert { RowId(it) }

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val proposal = client.getProposal(id) ?: return println("Proposal $id not found")
        val proposedBy = client.getProviderData(PubKey(proposal.proposedBy))
        val votingResults = client.getProposalVotingResults(proposal.id)
        println("""
            Proposal:       ${proposal.id.id} - ${proposal.type.name}
            Proposed by:    ${proposedBy.pubkey.hex()}${if (proposedBy.name.isNotEmpty()) " - " + proposedBy.name else ""}
            Time:           ${Date.from(Instant.ofEpochMilli(proposal.timestamp))}
            Positive votes: ${votingResults.positiveVotes}
            Negative votes: ${votingResults.negativeVotes}
            Max votes:      ${votingResults.maxVotes}
            Threshold:      ${formatThreshold(votingResults.threshold)}
            Status:         ${votingResults.votingResult}
        """.trimIndent())

        println("\nProposal details")
        println("------------------------------")
        println(formatProposal(client, proposal))
    }

    private fun formatProposal(client: PostchainClient, proposal: GetProposalResult): String {
        return when (proposal.type) {
            ProposalType.bc -> {
                val p = client.getBlockchainProposal(proposal.id) ?: return ""
                val conf = GtvDecoder.decodeGtv(p.data.data)
                "Container: ${p.container}\nData: $conf"
            }
            ProposalType.configuration -> {
                val p = client.getConfigurationProposal(proposal.id) ?: return ""
                val currentConf = GtvDecoder.decodeGtv(p.currentConf.data.data) as GtvDictionary
                val newConf = GtvDecoder.decodeGtv(p.proposedConf.data.data) as GtvDictionary
                "Proposed configuration:\n\n${GtvDiffFinder.diff(currentConf, newConf).diff}"
            }
            ProposalType.configuration_at -> {
                val p = client.getConfigurationProposalAt(proposal.id) ?: return ""
                val currentConf = GtvDecoder.decodeGtv(p.currentConf.data.data) as GtvDictionary
                val newConf = GtvDecoder.decodeGtv(p.proposedConf.data.data) as GtvDictionary
                "Enabled at height: ${p.proposedConf.height}\n\n${GtvDiffFinder.diff(currentConf, newConf).diff}"
            }
            ProposalType.voter_set_update -> {
                val vsu = client.getVoterSetUpdateProposal(proposal.id.id) ?: return ""
                val t = table {
                    row("Voter set:", vsu.voterSet)
                    row("Governor update", vsu.governor ?: "")
                    row("Majority threshold update", vsu.threshold ?: "")
                    row("New member", vsu.addMember.joinToString(", ") { it.toHex() })
                    row("Remove member", vsu.removeMember.joinToString(", ") { it.toHex() })
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render()
                return t.toString()
            }
            ProposalType.cluster_provider -> {
                val cpc = client.getClusterProviderProposal(proposal.id) ?: return ""
                return table {
                    row("Cluster:", cpc.cluster)
                    row("Provider:", cpc.provider.toHex())
                    row("Add/Remove:", if (cpc.add) "Add" else "remove")
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }
                        .render()
                        .toString()
            }
            ProposalType.provider_is_system -> {
                val pis = client.getSystemProviderProposal(proposal.id) ?: return ""
                return table {
                    row("Provider:", pis.provider.toHex())
                    row("Add/Remove:", if (pis.add) "Add" else "remove")
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()
            }
            ProposalType.provider_quota -> {
                val ppq = client.getProviderQuotaProposal(proposal.id) ?: return ""
                return table {
                    row("Provider tier:", ppq.tier.name)
                    row("Quota type:", ppq.quotaType.name)
                    row("Value:", ppq.value.toString())
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()
            }
            ProposalType.providers_batch -> {
                val ppb = client.getProvidersBatchProposal(proposal.id) ?: return ""
                val providers = table {
                    header("Pubkey", "Name", "Url")
                    ppb.providerInfos.forEach {
                        row(it.pubkey, it.name, it.url)
                    }
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()

                val info = table {
                    row("Provider tier:", ppb.tier.toString())
                    row("System:", ppb.system.toString())
                    row("Active:", ppb.active.toString())
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()

                return providers + "\n" + info
            }
            ProposalType.container_limits -> {
                val pcl = client.getContainerLimitsProposal(proposal.id) ?: return ""
                return table {
                    row("Container:", pcl.container)
                    row("Max blockchains:", pcl.maxBlockchains.toString())
                    row("CPU:", pcl.cpu.toString())
                    row("RAM (MB):", pcl.ram.toString())
                    row("Storage (MB):", pcl.storage.toString())
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()
            }
            ProposalType.cluster_limits -> {
                val pcl = client.getClusterLimitsProposal(proposal.id) ?: return ""
                return table {
                    row("Cluster:", pcl.cluster)
                    row("Max containers:", pcl.maxContainers.toString())
                    row("Default container max blockchains:", pcl.defaultContainerMaxBlockchains.toString())
                    row("Default container CPU:", pcl.defaultContainerCpu.toString())
                    row("Default container RAM (MB):", pcl.defaultContainerRam.toString())
                    row("Default container storage (MB):", pcl.defaultContainerStorage.toString())
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()
            }
            ProposalType.cluster_remove -> {
                val cluster = client.getClusterRemoveProposal(proposal.id) ?: return ""
                return "Cluster to remove: $cluster"
            }
            ProposalType.provider_state -> {
                val pps = client.getProviderStateProposal(proposal.id) ?: return ""
                return table {
                    row("Provider:", pps.provider.toHex())
                    row("Provider name:", pps.providerName)
                    row("Enable/Disable:", if (pps.active) "Enable" else "Disable")
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()
            }
            ProposalType.blockchain_action -> {
                val pba = client.getBlockchainActionProposal(proposal.id) ?: return ""
                return table {
                    row("Blockchain:", pba.blockchain.toHex())
                    row("Blockchain name:", pba.blockchainName)
                    row("Action:", pba.action.name)
                    hints { defaultAlignment = Table.Hints.Alignment.LEFT }
                }.render().toString()
            }
            ProposalType.other -> "No details"
        }
    }
}
