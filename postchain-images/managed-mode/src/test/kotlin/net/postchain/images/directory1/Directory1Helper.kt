package net.postchain.images.directory1

import assertk.assertions.isTrue
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.TxBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory

class Directory1Helper(private val postchainContainer: PostchainContainer, private val chain0: BlockchainRid) {

    private val client get() = postchainContainer.client(chain0)

    fun getBlockchainSigners(blockchain: Gtv): Array<out Gtv> {
        return awaitQueryResult {
            client.query("get_blockchain_signers", GtvFactory.gtv("bc" to blockchain)).get().asArray()
        }!!
    }

    fun getBlockchainGtv(blockchainRid: BlockchainRid): Gtv {
        return awaitQueryResult {
            client.querySync("get_blockchain", GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRid.data)))
        }!!
    }

    fun getProvider(pubkey: ByteArray = initialProviderPubKey): Gtv {
        return awaitQueryResult {
            client.query("get_provider", GtvFactory.gtv("pubkey" to GtvFactory.gtv(pubkey))).get()
        }!!
    }


    fun getSystemCluster(): Gtv {
        return awaitQueryResult {
            client.query("get_cluster", GtvFactory.gtv("name" to GtvFactory.gtv("system"))).get()
        }!!
    }

    fun getSystemContainer(): Gtv {
        return awaitQueryResult {
            client.query("get_container", GtvFactory.gtv("name" to GtvFactory.gtv("system"))).get()
        }!!
    }


    fun getAllBlockchains(): Gtv {
        return awaitQueryResult {
            client.query("get_blockchains", GtvFactory.gtv("include_inactive" to GtvFactory.gtv(true))).get()
        }!!
    }

    fun addNode(newNode: PostchainContainer, newNodeProvider: Gtv, cluster: Gtv, brid0: BlockchainRid) {
        val opName = "add_node"
        TxBuilder(brid0, newNode.sigMaker).build( opName,
            newNodeProvider,
            GtvFactory.gtv(newNode.pubKeyByteArray),
            GtvFactory.gtv(newNode.nodeHost),
            GtvFactory.gtv(newNode.nodePort.toLong()),
            cluster
        ).also {
            postchainContainer.txBldr(it, opName)
        }
        awaitQueryResult {
            val isNode = client.querySync("is_node",
                GtvFactory.gtv("pubkey" to GtvFactory.gtv(newNode.pubKeyByteArray))
            )
            assertk.assert(isNode.asBoolean(), "${newNode.nodeHost} is added to ${postchainContainer.nodeHost}").isTrue()
        }
    }
}
