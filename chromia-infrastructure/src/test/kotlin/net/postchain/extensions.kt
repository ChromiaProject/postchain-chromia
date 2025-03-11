package net.postchain

import net.postchain.client.core.PostchainQuery
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.concurrent.util.get
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import org.mockito.kotlin.mock

fun IntegrationTestSetup.query(chainId: Long): PostchainQuery =
        PostchainQuery { name, args -> getChainNodes(chainId).first().blockQueries(chainId).query(name, args).get() }

fun IntegrationTestSetup.enqueueTx(
        chainId: Long,
        merkleHashCalculator: GtvMerkleHashCalculatorBase,
        body: (TransactionBuilder) -> Unit
) {
    val blockchainRid = getChainNodes(chainId).first().getBlockchainRid(chainId)!!
    val builder = TransactionBuilder(mock(), blockchainRid, emptyList(), merkleHashCalculator, cryptoSystem = cryptoSystem)
    body(builder)
    val txData = builder
            .addNop()
            .finish().buildGtx().encode()
    enqueueTx(chainId, txData)
}
