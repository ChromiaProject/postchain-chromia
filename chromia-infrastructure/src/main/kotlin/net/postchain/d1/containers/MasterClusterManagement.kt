package net.postchain.d1.containers

import net.postchain.client.core.PostchainClient
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.ClusterManagementImpl

class MasterClusterManagement(
        private val postchainClient: PostchainClient
) : ClusterManagement by ClusterManagementImpl(postchainClient::querySync)
