package net.postchain.d1.iccf

import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider

class IccfGTXModuleContext {
    lateinit var cryptoSystem: CryptoSystem
    lateinit var clusterManagement: ClusterManagement
    lateinit var queryProvider: ChromiaQueryProvider
    var nodeIsReplica = false
}
