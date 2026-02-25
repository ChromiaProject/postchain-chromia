package net.postchain.hybridcompute.engine

import net.postchain.PostchainContext
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.PostchainContextAware
import net.postchain.hybridcompute.HybridComputeEngine
import net.postchain.hybridcompute.rell.lib.hybridcompute_query.QueryRequest
import java.time.Duration

class QueryComputeEngine: HybridComputeEngine, PostchainContextAware {

    companion object {
        const val HYBRIDCOMPUTE_QUERY_CONFIG_NAME = "hybridcompute_query"
    }

    override val name = "query"

    private lateinit var configuration: BlockchainConfiguration
    private lateinit var postchainContext: PostchainContext
    private lateinit var blockQueries: BlockQueries
    private lateinit var queryTimeout: Duration

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        this.postchainContext = postchainContext
        this.configuration = configuration
        val computeConfig = configuration.rawConfig[HYBRIDCOMPUTE_QUERY_CONFIG_NAME]?.toObject<QueryComputeConfig>()
                ?: QueryComputeConfig.DEFAULT_CONFIG
        queryTimeout = Duration.ofSeconds(computeConfig.timeoutSeconds)
    }

    override fun load() {
        blockQueries = postchainContext.blockQueriesProvider.getBlockQueries(configuration.blockchainRid)
                ?: throw ProgrammerMistake("Failed to get block queries for ${configuration.blockchainRid}")
    }

    override fun compute(input: Gtv): Pair<Gtv, Long> {
        val queryRequest = GtvObjectMapper.fromGtv(input, QueryRequest::class.java)

        if (!configuration.hasQuery(queryRequest.name)) {
            throw UserMistake("Query ${queryRequest.name} not found")
        }

        val result = blockQueries.queryWithTimeout(queryRequest.name, gtv(queryRequest.args),
                queryTimeout = queryTimeout, lockTimeout = queryTimeout).get()
        return result to 0
    }

    override fun validate(input: Gtv, output: Gtv) {
        val queryRequest = input.toObject<QueryRequest>()

        val validationOutput = blockQueries.queryWithTimeout(queryRequest.name, gtv(queryRequest.args),
                queryTimeout = queryTimeout, lockTimeout = queryTimeout).get()

        if (output != validationOutput) {
            throw UserMistake("Query result mismatch")
        }
    }

    override fun estimatePoints(input: Gtv): Long = 0
}