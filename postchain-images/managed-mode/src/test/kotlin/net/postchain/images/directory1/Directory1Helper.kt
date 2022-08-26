package net.postchain.images.directory1

import assertk.assertions.isTrue
import net.postchain.common.BlockchainRid
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.TxBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

class Directory1Helper(private val postchainContainer: PostchainContainer, private val chain0: BlockchainRid) {

    private val client get() = postchainContainer.client(chain0)

    fun getBlockchainSigners(blockchain: Gtv): Array<out Gtv> {
        return awaitQueryResult {
            client.querySync("get_blockchain_signers", gtv("bc" to blockchain)).asArray()
        }!!
    }

    fun getBlockchainGtv(blockchainRid: BlockchainRid): Gtv {
        return awaitQueryResult {
            client.querySync("get_blockchain", gtv("rid" to gtv(blockchainRid.data)))
        }!!
    }

    fun getProvider(pubkey: ByteArray = initialProviderPubKey): Gtv {
        return awaitQueryResult {
            client.querySync("get_provider", gtv("pubkey" to gtv(pubkey)))
        }!!
    }


    fun getSystemCluster(): Gtv {
        return awaitQueryResult {
            client.querySync("get_cluster", gtv("name" to gtv("system")))
        }!!
    }

    fun getSystemContainer(): Gtv {
        return getContainer("system")
    }

    fun getContainer(name: String): Gtv {
        return awaitQueryResult {
            client.querySync("get_container", gtv("name" to gtv(name)))
        }!!
    }

    fun getSystemDeployer(): Gtv {
        return awaitQueryResult {
            client.querySync("get_voter_set", gtv("name" to gtv("SYSTEM_P")))
        }!!
    }

    fun getAllBlockchains(): Gtv {
        return awaitQueryResult {
            client.querySync("get_blockchains", gtv("include_inactive" to gtv(true)))
        }!!
    }

    fun getAllContainers(): Gtv {
        return awaitQueryResult {
            client.querySync("get_containers")
        }!!
    }

    fun getContainerResourceLimits(name: String): Gtv {
        return awaitQueryResult {
            client.querySync("nm_get_container_limits", gtv("name" to gtv(name)))
        }!!
    }

    fun addNode(newNode: PostchainContainer, newNodeProvider: Gtv, cluster: Gtv, brid0: BlockchainRid) {
        val opName = "add_node"
        TxBuilder(brid0, newNode.sigMaker).build(opName,
                newNodeProvider,
                gtv(newNode.pubKeyByteArray),
                gtv(newNode.nodeHost), gtv(newNode.nodePort.toLong()),
                cluster
        ).also {
            postchainContainer.txBldr(it, opName)
        }
        awaitQueryResult {
            val isNode = client.querySync("is_node",
                    gtv("pubkey" to gtv(newNode.pubKeyByteArray))
            )
            assertk.assert(isNode.asBoolean(), "${newNode.nodeHost} is added to ${postchainContainer.nodeHost}").isTrue()
        }
    }
}
