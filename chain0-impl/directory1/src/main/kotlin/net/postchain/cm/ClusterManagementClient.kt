package net.postchain.cm

import net.postchain.client.core.PostchainQuery
import net.postchain.gtv.Gtv

class ClusterManagementClient(val query: (String, Gtv) -> Gtv): PostchainQuery {
    override fun querySync(name: String, gtv: Gtv) = query(name, gtv)
}
