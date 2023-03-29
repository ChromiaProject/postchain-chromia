package net.postchain.mc.network

import net.postchain.chain0.model.NodeInfo
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.EndpointPool
import java.net.InetSocketAddress

class NodeVerifyer(private val configTemplate: PostchainClientConfig) {

    fun verifyHost(node: NodeInfo): Boolean {
        return verifyHost(node.host, node.port.toInt())
    }

    fun verifyHost(host: String, port: Int): Boolean {
        return try {
            val socketAddress = InetSocketAddress(host, port)
            socketAddress.address.isReachable(1000)
        } catch (e: Exception) {
            println(e)
            false
        }
    }


    fun verifyApi(node: NodeInfo) = verifyApi(node.apiUrl)

    fun verifyApi(url: String = configTemplate.endpointPool.first().url): Pair<Boolean, Long?> {
        return try {
            val nodeClient = PostchainClientImpl(configTemplate.copy(endpointPool = EndpointPool.singleUrl(url)))
            true to nodeClient.currentBlockHeight()
        } catch (e: Exception) {
            false to null
        }
    }
}
