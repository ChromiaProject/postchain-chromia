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

internal fun Context.registerNodeAsProvider(cluster: Gtv, newNode: PostchainContainer): Gtv {
    node.txAsAdmin(0, "register_provider", provider, gtv(newNode.pubKeyByteArray), gtv(1L))
    db.awaitNewBlock()
    val newProvider = awaitQueryResult {
        node.client(0).querySync("get_provider", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
    }!!

    node.txAsAdmin(0, "add_provider_to_cluster", provider, newProvider, cluster)
    db.awaitNewBlock()

    node.txAsAdmin(0, "propose_enable_provider", provider, newProvider)
    db.awaitNewBlock()
    approverNode?.approveProposal(approver)

    node.txAsAdmin(0, "propose_provider_is_system", provider, newProvider, gtv(true))
    approverNode?.approveProposal(approver)

    // Asserting: provider2 is added correctly
    val newProviderData = awaitQueryResult {
        node.client(0).querySync("get_provider_data", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
    }!!
    assertArrayEquals(newProviderData["pubkey"]?.asByteArray(), newNode.pubKeyByteArray)
    assert(newProviderData["name"]?.asString()).isEqualTo("")
    assert(newProviderData["active"]?.asBoolean()).isEqualTo(true)

    return newProvider
}

internal fun PostchainContainer.approveProposal(provider: Gtv?): Gtv? {
    var proposal: Gtv? = null
    if (provider != null) {
        proposal = client(0).getProposal()

        // FYI: if (node == node1) then use node.txAsAdmin()
        if (networkAliases.contains("node1")) {
            txAsAdmin(0, "make_vote", provider, proposal!!, gtv(true))
        } else {
            tx(0, "make_vote", provider, proposal!!, gtv(true))
        }
    }
    return proposal
}

internal fun PostchainContainer.getBlockchainSigners(blockchain: Gtv): Array<out Gtv> {
    return awaitQueryResult {
        client(0).query("get_blockchain_signers", gtv("bc" to blockchain)).get().asArray()
    }!!
}

internal fun PostchainContainer.getBlockchainGtv(blockchainRid: BlockchainRid): Gtv {
    return awaitQueryResult {
        client(0).querySync("get_blockchain", gtv("rid" to gtv(blockchainRid.data)))
    }!!
}

internal fun PostchainClient.getProvider1(): Gtv {
    return awaitQueryResult {
        query("get_provider", gtv("pubkey" to gtv(initialProviderPubKey))).get()
    }!!
}

internal fun PostchainContainer.getProvider(): Gtv {
    return awaitQueryResult {
        client(0).query("get_provider", gtv("pubkey" to gtv(pubKeyByteArray))).get()
    }!!
}

internal fun PostchainClient.getSystemCluster(): Gtv {
    return awaitQueryResult {
        query("get_cluster", gtv("name" to gtv("system"))).get()
    }!!
}

internal fun PostchainClient.getSystemContainer(): Gtv {
    return awaitQueryResult {
        query("get_container", gtv("name" to gtv("system"))).get()
    }!!
}

internal fun PostchainContainer.getAllBlockchains(): Gtv {
    return awaitQueryResult {
        client(0).query("get_blockchains", gtv("include_inactive" to gtv(true))).get()
    }!!
}

internal fun PostchainClient.getProposal(): Gtv {
    return awaitQueryResult {
        query("get_proposals_since", gtv("since" to gtv(0L))).get().asArray().first()
                .asDict()["rowid"]!!
    }!!
}

internal fun addNode(newNode: PostchainContainer, newNodeProvider: Gtv, cluster: Gtv, brid0: BlockchainRid, sendTxTo: PostchainContainer) {
    TxBuilder(brid0, newNode.sigMaker).build("add_node",
            newNodeProvider,
            gtv(newNode.pubKeyByteArray),
            gtv(newNode.nodeHost),
            gtv(newNode.nodePort.toLong()),
            cluster
    ).also {
        sendTxTo.txBldr(it, "add_node")
    }
    awaitQueryResult {
        val isNode = sendTxTo.client(0).querySync("is_node", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
        assert(isNode.asBoolean(), "${newNode.nodeHost} is added to ${sendTxTo.nodeHost}").isTrue()
    }
}
