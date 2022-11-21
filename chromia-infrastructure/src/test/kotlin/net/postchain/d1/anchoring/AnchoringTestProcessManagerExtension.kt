package net.postchain.d1.anchoring

import net.postchain.PostchainContext
import net.postchain.managed.config.ManagedDataSourceAware

class AnchoringTestProcessManagerExtension(postchainContext: PostchainContext) : AnchoringProcessManagerExtension(postchainContext) {
   override fun createClusterManagement(configuration: ManagedDataSourceAware) = AnchoringTestClusterManagement()
}
