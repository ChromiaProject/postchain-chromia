package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.managed.config.ManagedDataSourceAware

class AnchorTestProcessManagerExtension(postchainContext: PostchainContext) : AnchorProcessManagerExtension(postchainContext) {
   override fun createClusterManagement(configuration: ManagedDataSourceAware) = AnchorTestClusterManagement()
}
