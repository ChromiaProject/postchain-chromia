package net.postchain.d1.icmf

import net.postchain.client.config.FailOverConfig
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.Queries
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.format.Gson.auto
import org.http4k.lens.Path
import org.http4k.lens.long
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.io.Closeable

object MockPostchainRestApi : HttpHandler, Closeable {
    private val port = 9000

    private val mockClients = mutableMapOf<BlockchainRid, PostchainClient>()

    fun createProvider(clusterManagement: ClusterManagement): ChromiaClientProvider =
            object : ChromiaClientProvider(FailOverConfig(), clusterManagement) {
                override fun cluster(clusterName: String): ClusterPostchainClient =
                        ClusterPostchainClient(EndpointPool.singleUrl("http://localhost:$port"))
            }

    fun addMockClient(blockchainRid: BlockchainRid, client: PostchainClient) {
        mockClients[blockchainRid] = client
    }

    fun clearMocks() {
        mockClients.clear()
    }

    private val jsonLens = Body.string(ContentType.APPLICATION_JSON).toLens()

    private val blockchainRid = Path.string().of("blockchainRID")
    private val height = Path.long().of("height")

    private val app = ServerFilters.CatchLensFailure.then(
            routes(
                    "/query_gtx/{blockchainRID}" bind Method.POST to { request ->
                        val blockchainRid = BlockchainRid(blockchainRid(request).hexStringToByteArray())
                        val queries = Body.auto<Queries>().toLens()
                        val gtvQuery = GtvDecoder.decodeGtv(queries(request).queries[0].hexStringToByteArray()).asArray()
                        val queryName = gtvQuery[0].asString()
                        val queryArgs = gtvQuery[1]

                        val clientMock = mockClients[blockchainRid]
                        if (clientMock == null) {
                            Response(Status.NOT_FOUND)
                        } else {
                            val responseGtv = clientMock.querySync(queryName, queryArgs)
                            Response(Status.OK).with(Body.auto<List<String>>().toLens() of listOf(GtvEncoder.encodeGtv(responseGtv).toHex()))
                        }
                    },
                    "/blocks/{blockchainRID}/height/{height}" bind Method.GET to { request ->
                        val blockchainRid = BlockchainRid(blockchainRid(request).hexStringToByteArray())
                        val height = height(request)

                        val clientMock = mockClients[blockchainRid]
                        if (clientMock == null) {
                            Response(Status.NOT_FOUND)
                        } else {
                            val block: BlockDetail? = clientMock.blockAtHeightSync(height)
                            if (block == null) {
                                Response(Status.OK).with(jsonLens of "null")
                            } else {
                                Response(Status.OK).with(Body.auto<BlockDetail>().toLens() of block)
                            }
                        }
                    }
            )
    )

    override fun invoke(request: Request): Response = app(request)

    private var server: Http4kServer? = null

    fun start() {
        server = app.asServer(SunHttp(port)).start()
    }

    override fun close() {
        server?.stop()
        server = null
    }
}
