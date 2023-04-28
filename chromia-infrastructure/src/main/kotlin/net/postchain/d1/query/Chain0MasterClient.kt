package net.postchain.d1.query

import net.postchain.client.core.PostchainQuery
import net.postchain.gtv.Gtv
import net.postchain.network.mastersub.MasterSubQueryManager

class Chain0MasterClient(
        private val queryManager: MasterSubQueryManager,
) : PostchainQuery {
    override fun query(name: String, args: Gtv): Gtv = queryManager.query(
            null,
            name,
            args
    ).toCompletableFuture().get()
}