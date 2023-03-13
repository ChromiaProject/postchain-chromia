package net.postchain.mc.network

import net.postchain.chain0.model.NodeInfo
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.SingleEndpointPool
import java.net.InetSocketAddress

class NodeVerifyer(private val configTemplate: PostchainClientConfig) {

    fun verifyHost(node: NodeInfo): Boolean {
        return try {
            val socketAddress = InetSocketAddress(node.host, node.port.toInt())
            socketAddress.address.isReachable(1000)
        } catch (e: Exception) {
            println(e)
            false
        }
    }

    fun verifyApi(node: NodeInfo): Pair<Boolean, Long?> {
        return try {
            val nodeClient = PostchainClientImpl(configTemplate.copy(endpointPool = SingleEndpointPool(node.apiUrl)))
            true to nodeClient.currentBlockHeight()
        } catch (e: Exception) {
            false to null
        }
    }
}
