package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.TxBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChainDatabaseCommunicator
import org.junit.jupiter.api.Assertions.assertArrayEquals

data class Context(
        val node: PostchainContainer,
        val db: ChainDatabaseCommunicator,
        val provider: Gtv,
        val approverNode: PostchainContainer? = null,
        val approver: Gtv? = null
)

internal fun Context.registerNodeAsProvider(brid: BlockchainRid, cluster: Gtv, newNode: PostchainContainer): Gtv {
    node.txAsAdmin(brid, "register_provider", provider, gtv(newNode.pubKeyByteArray), gtv(1L))
    db.awaitNewBlock()
    val newProvider = awaitQueryResult {
        node.client(brid).querySync("get_provider", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
    }!!

    node.txAsAdmin(brid, "add_provider_to_cluster", provider, newProvider, cluster)
    db.awaitNewBlock()

    node.txAsAdmin(brid, "propose_enable_provider", provider, newProvider)
    db.awaitNewBlock()
    approverNode?.approveProposal(brid, approver)

    node.txAsAdmin(brid, "propose_provider_is_system", provider, newProvider, gtv(true))
    approverNode?.approveProposal(brid, approver)

    // Asserting: provider2 is added correctly
    val newProviderData = awaitQueryResult {
        node.client(brid).querySync("get_provider_data", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
    }!!
    assertArrayEquals(newProviderData["pubkey"]?.asByteArray(), newNode.pubKeyByteArray)
    assert(newProviderData["name"]?.asString()).isEqualTo("")
    assert(newProviderData["active"]?.asBoolean()).isEqualTo(true)

    return newProvider
}

internal fun PostchainContainer.approveProposal(brid: BlockchainRid, provider: Gtv?): Gtv? {
    var proposal: Gtv? = null
    if (provider != null) {
        proposal = client(brid).getProposal()

        // FYI: if (node == node1) then use node.txAsAdmin()
        if (networkAliases.contains("node1")) {
            txAsAdmin(brid, "make_vote", provider, proposal!!, gtv(true))
        } else {
            tx(brid, "make_vote", provider, proposal!!, gtv(true))
        }
    }
    return proposal
}

internal fun PostchainClient.getProposal(): Gtv {
    return awaitQueryResult {
        query("get_proposals_since", gtv("since" to gtv(0L))).get().asArray().first()
                .asDict()["rowid"]!!
    }!!
}
