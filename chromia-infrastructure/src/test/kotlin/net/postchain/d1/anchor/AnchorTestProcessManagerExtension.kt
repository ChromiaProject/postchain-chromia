package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.managed.config.ManagedDataSourceAwareness

class AnchorTestProcessManagerExtension(postchainContext: PostchainContext) : AnchorProcessManagerExtension(postchainContext) {
   override fun createClusterManagement(configuration: ManagedDataSourceAwareness) = AnchorTestClusterManagement()
}
