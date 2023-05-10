package net.postchain.mc.network

import net.postchain.chain0.model.NodeInfo
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class NodeVerifier(private val configTemplate: PostchainClientConfig, private val sacBrid: BlockchainRid?) {

    fun verifyHost(node: NodeInfo): Boolean {
        return verifyHost(node.host, node.port.toInt())
    }

    fun verifyHost(host: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.close()
            true
        } catch (e: IOException) {
            println(e)
            false
        }
    }


    fun verifyApi(node: NodeInfo) = verifyApi(node.apiUrl)

    fun verifyApi(url: String = configTemplate.endpointPool.first().url): NodeApiStatus {
        return try {
            val nodeClient = PostchainClientImpl(configTemplate.copy(endpointPool = EndpointPool.singleUrl(url)))
            NodeApiStatus(
                    true,
                    nodeClient.currentBlockHeight(),
                    sacBrid?.let { brid -> PostchainClientImpl(nodeClient.config.copy(blockchainRid = brid)).currentBlockHeight() }
            )
        } catch (e: Exception) {
            NodeApiStatus.notResponding()
        }
    }

    data class NodeApiStatus(val responds: Boolean, val height: Long?, val systemAnchorHeight: Long?) {
        companion object {
            fun notResponding() = NodeApiStatus(false, null, null)
        }
    }
}
