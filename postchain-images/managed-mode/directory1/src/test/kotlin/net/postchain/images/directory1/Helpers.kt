package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.TxBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChainDatabaseCommunicator
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.io.File
import java.nio.file.Files

internal fun PostchainContainer.proposeChain0(provider: Gtv) {
    val containerConfig0 = "${envMap["RELL_OUT"] ?: "${PostchainContainer.RELL_PATH}/out"}/blockchains/0/0.gtv"
    val hostConfig0 = Files.createTempDirectory("").toAbsolutePath().toString() + "0.gtv"
    copyFileFromContainer(containerConfig0, hostConfig0)
    val configGtv = GtvFactory.gtv(File(hostConfig0).readBytes())

    val containerGtv = client(0).query(
            "get_container", GtvFactory.gtv("name" to GtvFactory.gtv("system"))).get()

    txAsAdmin(0, "propose_blockchain", provider, configGtv, containerGtv)
}

data class Context(
        val node: PostchainContainer,
        val db: ChainDatabaseCommunicator,
        val provider: Gtv
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

    node.txAsAdmin(0, "propose_provider_is_system", provider, newProvider, gtv(true))

    // Asserting: provider2 is added correctly
    val newProviderData = awaitQueryResult {
        node.client(0).querySync("get_provider_data", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
    }!!
    assertArrayEquals(newProviderData["pubkey"]?.asByteArray(), newNode.pubKeyByteArray)
    assert(newProviderData["name"]?.asString()).isEqualTo("")
    assert(newProviderData["active"]?.asBoolean()).isEqualTo(true)

    return newProvider
}

internal fun PostchainClient.getBlockchainSigners(blockchain: Gtv): Array<out Gtv> {
    return query("get_blockchain_signers", GtvFactory.gtv("bc" to blockchain)).get().asArray()
}

internal fun PostchainClient.getProvider1(): Gtv {
    return query("get_provider", gtv("pubkey" to gtv(initialProviderPubKey))).get()
}

internal fun PostchainContainer.getProvider(): Gtv {
    return client(0).query("get_provider", gtv("pubkey" to gtv(pubKeyByteArray))).get()
}

internal fun PostchainClient.getSystemCluster(): Gtv {
    return query("get_cluster", gtv("name" to gtv("system"))).get()
}

internal fun PostchainClient.getSystemContainer(): Gtv {
    return query("get_container", gtv("name" to gtv("system"))).get()
}

internal fun PostchainClient.getAllBlockchains(): Gtv {
    return query("get_blockchains", gtv("include_inactive" to gtv(true))).get()
}

internal fun PostchainClient.getProposal(): Gtv {
    return query("get_proposals_since", gtv("since" to gtv(0L))).get().asArray().first()
            .asDict()["rowid"]!!
}


internal fun addNode(newNode: PostchainContainer, newNodeProvider: Gtv, cluster: Gtv, brid0: BlockchainRid, sendTxTo: PostchainContainer) {
    TxBuilder(brid0, newNode.sigMaker).build("add_node",
            newNodeProvider,
            gtv(newNode.pubKeyByteArray),
            gtv(newNode.nodeHost), gtv(newNode.nodePort.toLong()),
            cluster
    ).also {
        sendTxTo.tx(it)
    }
    awaitQueryResult {
        val isNode = sendTxTo.client(0).querySync("is_node", gtv("pubkey" to gtv(newNode.pubKeyByteArray)))
        assert(isNode.asBoolean(), "${newNode.nodeHost} is added to ${sendTxTo.nodeHost}").isTrue()
    }
}
