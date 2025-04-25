package net.postchain.d1.icmf

import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.BlockchainNodeState
import net.postchain.api.rest.InfraVersion
import net.postchain.api.rest.TransactionsCount
import net.postchain.api.rest.Version
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiRejectedTransaction
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.ConfirmationProof
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockDetailsTruncated
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.crypto.PubKey
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.gtv.Gtv
import net.postchain.gtx.GtxQuery
import java.io.Closeable
import java.time.Instant

object MockPostchainRestApi : Closeable {
    private const val port = 9000

    fun createProvider(clusterManagement: ClusterManagement): ChromiaClientProvider =
            object : ChromiaClientProvider(clusterManagement) {
                override fun cluster(clusterName: String): ClusterPostchainClient =
                        ClusterPostchainClient(EndpointPool.singleUrl("http://localhost:$port"))

                override fun blockchain(blockchainRid: BlockchainRid): PostchainClient =
                        super.client(blockchainRid, EndpointPool.singleUrl("http://localhost:$port"))
            }

    fun addMockClient(blockchainRid: BlockchainRid, chainIid: Long, client: PostchainClient) {
        server!!.attachModel(blockchainRid, object : Model {
            override val blockchainRid: BlockchainRid = blockchainRid
            override val chainIID: Long = chainIid
            override var live: Boolean = true
            override val queryCacheTtlSeconds: Long = 0

            override fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail? = client.blockAtHeight(height)?.let {
                BlockDetail(
                        rid = it.rid.data,
                        prevBlockRID = it.prevBlockRID.data,
                        header = it.header.data,
                        height = it.height,
                        transactions = it.transactions.map { tx ->
                            TxDetail(
                                    rid = tx.rid.data,
                                    hash = tx.hash.data,
                                    data = tx.data?.data)
                        },
                        witness = it.witness.data,
                        timestamp = it.timestamp,
                )
            }

            override fun query(query: GtxQuery): Gtv = client.query(query.name, query.args)

            override fun postTransaction(tx: ByteArray) {
                TODO("Not yet implemented")
            }

            override fun getTransaction(txRID: TxRid): ByteArray? {
                TODO("Not yet implemented")
            }

            override fun getTransactionInfo(txRID: TxRid, includeTxData: Boolean): TransactionInfoExt? {
                TODO("Not yet implemented")
            }

            override fun getTransactionsInfo(timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): TransactionInfoExtsTruncated {
                TODO("Not yet implemented")
            }

            override fun getTransactionsInfoBySigner(timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): TransactionInfoExtsTruncated {
                TODO("Not yet implemented")
            }

            override fun getLastTransactionNumber(): TransactionsCount {
                TODO("Not yet implemented")
            }

            override fun getBlock(blockRID: BlockRid, txHashesOnly: Boolean): BlockDetail? {
                TODO("Not yet implemented")
            }

            override fun confirmBlock(blockRID: BlockRid): BlockSignature? {
                TODO("Not yet implemented")
            }

            override fun getBlocksBetweenTimes(timeFilter: BlockQueryTimeFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated {
                TODO("Not yet implemented")
            }

            override fun getBlocksBetweenHeights(heightFilter: BlockQueryHeightFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated {
                TODO("Not yet implemented")
            }

            override fun getConfirmationProof(txRID: TxRid): ConfirmationProof? {
                TODO("Not yet implemented")
            }

            override fun getStatus(txRID: TxRid): ApiStatus {
                TODO("Not yet implemented")
            }

            override fun getWaitingTransactions(): List<TxRid> {
                TODO("Not yet implemented")
            }

            override fun getWaitingTransaction(txRID: TxRid): Pair<ByteArray, Instant>? {
                TODO("Not yet implemented")
            }

            override fun getRejectedTransactions(): List<ApiRejectedTransaction> {
                TODO("Not yet implemented")
            }

            override fun nodeStatusQuery(): StateNodeStatus {
                TODO("Not yet implemented")
            }

            override fun nodePeersStatusQuery(): List<StateNodeStatus> {
                TODO("Not yet implemented")
            }

            override fun getCurrentBlockHeight(): BlockHeight {
                TODO("Not yet implemented")
            }

            override fun getBlockchainNodeState(): BlockchainNodeState {
                TODO("Not yet implemented")
            }

            override fun getBlockchainConfiguration(height: Long): ByteArray? {
                TODO("Not yet implemented")
            }

            override fun validateBlockchainConfiguration(configuration: Gtv) {
                TODO("Not yet implemented")
            }

            override fun getNextBlockchainConfigurationHeight(height: Long): BlockHeight? {
                TODO("Not yet implemented")
            }

            override fun getVersion(): Version {
                TODO("Not yet implemented")
            }

            override fun getInfrastructureVersion(): InfraVersion {
                TODO("Not yet implemented")
            }
        })
    }

    private var server: RestApi? = null

    fun start() {
        server = RestApi(listenPort = port, basePath = "", gracefulShutdown = false)
    }

    override fun close() {
        server?.close()
        server = null
    }
}
