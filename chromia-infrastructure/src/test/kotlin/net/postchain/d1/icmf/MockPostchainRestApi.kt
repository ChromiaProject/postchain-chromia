package net.postchain.d1.icmf

import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.BlockchainNodeState
import net.postchain.api.rest.InfraVersion
import net.postchain.api.rest.TransactionsCount
import net.postchain.api.rest.Version
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiMetadata
import net.postchain.api.rest.model.ApiRejectedTransaction
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.ConfirmationProof
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.AsyncQueryResponse
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockDetailsTruncated
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
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

            override fun queryWithHeight(query: GtxQuery): Pair<Gtv, Long> = client.query(query.name, query.args) to 0

            override fun enqueueQuery(query: GtxQuery) {
                throw NotImplementedError("not used in mock")
            }

            override fun fetchQueryResponse(queryRid: WrappedByteArray): AsyncQueryResponse {
                throw NotImplementedError("not used in mock")
            }

            override fun postTransaction(tx: ByteArray) {
                throw NotImplementedError("not used in mock")
            }

            override fun getTransaction(txRID: TxRid): ByteArray? {
                throw NotImplementedError("not used in mock")
            }

            override fun getTransactionInfo(txRID: TxRid, includeTxData: Boolean): TransactionInfoExt? {
                throw NotImplementedError("not used in mock")
            }

            override fun getTransactionsInfo(timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): TransactionInfoExtsTruncated {
                throw NotImplementedError("not used in mock")
            }

            override fun getTransactionsInfoBySigner(timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): TransactionInfoExtsTruncated {
                throw NotImplementedError("not used in mock")
            }

            override fun getLastTransactionNumber(): TransactionsCount {
                throw NotImplementedError("not used in mock")
            }

            override fun getBlock(blockRID: BlockRid, txHashesOnly: Boolean): BlockDetail? {
                throw NotImplementedError("not used in mock")
            }

            override fun confirmBlock(blockRID: BlockRid): BlockSignature? {
                throw NotImplementedError("not used in mock")
            }

            override fun getBlocksBetweenTimes(timeFilter: BlockQueryTimeFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated {
                throw NotImplementedError("not used in mock")
            }

            override fun getBlocksBetweenHeights(heightFilter: BlockQueryHeightFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated {
                throw NotImplementedError("not used in mock")
            }

            override fun getConfirmationProof(txRID: TxRid): ConfirmationProof? {
                throw NotImplementedError("not used in mock")
            }

            override fun getStatus(txRID: TxRid): ApiStatus {
                throw NotImplementedError("not used in mock")
            }

            override fun getWaitingTransactions(): List<TxRid> {
                throw NotImplementedError("not used in mock")
            }

            override fun getWaitingTransaction(txRID: TxRid): Pair<ByteArray, Instant>? {
                throw NotImplementedError("not used in mock")
            }

            override fun getRejectedTransactions(): List<ApiRejectedTransaction> {
                throw NotImplementedError("not used in mock")
            }

            override fun checkQueryCorrectness(query: GtxQuery) {
                throw NotImplementedError("not used in mock")
            }

            override fun nodeStatusQuery(): StateNodeStatus {
                throw NotImplementedError("not used in mock")
            }

            override fun nodePeersStatusQuery(): List<StateNodeStatus> {
                throw NotImplementedError("not used in mock")
            }

            override fun getCurrentBlockHeight(): BlockHeight {
                throw NotImplementedError("not used in mock")
            }

            override fun getBlockchainNodeState(): BlockchainNodeState {
                throw NotImplementedError("not used in mock")
            }

            override fun getBlockchainConfiguration(height: Long): ByteArray? {
                throw NotImplementedError("not used in mock")
            }

            override fun validateBlockchainConfiguration(configuration: Gtv) {
                throw NotImplementedError("not used in mock")
            }

            override fun getNextBlockchainConfigurationHeight(height: Long): BlockHeight? {
                throw NotImplementedError("not used in mock")
            }

            override fun getVersion(): Version {
                throw NotImplementedError("not used in mock")
            }

            override fun getInfrastructureVersion(): InfraVersion {
                throw NotImplementedError("not used in mock")
            }

            override fun getMetadata(): ApiMetadata {
                throw NotImplementedError("not used in mock")
            }

            override fun getBlockSigMaker(): SigMaker {
                throw NotImplementedError("not used in mock")
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
