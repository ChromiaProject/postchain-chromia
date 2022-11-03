package net.postchain.d1.icmf

import com.google.gson.Gson
import net.postchain.client.config.FailOverConfig
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
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
    private const val port = 9000

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

    private val blockchainRid = Path.string().of("blockchainRID")
    private val height = Path.long().of("height")

    private val app = ServerFilters.CatchLensFailure.then(
            routes(
                    "/query_gtv/{blockchainRID}" bind Method.POST to { request ->
                        val blockchainRid = BlockchainRid(blockchainRid(request).hexStringToByteArray())
                        val gtvQuery = GtvDecoder.decodeGtv(request.body.stream)
                        val queryName = gtvQuery[0].asString()
                        val queryArgs = gtvQuery[1]

                        val clientMock = mockClients[blockchainRid]
                        if (clientMock == null) {
                            Response(Status.NOT_FOUND)
                        } else {
                            val responseGtv = clientMock.querySync(queryName, queryArgs)
                            Response(Status.OK).body(GtvEncoder.encodeGtv(responseGtv).inputStream())
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
                                Response(Status.OK).body("null")
                            } else {
                                Response(Status.OK).body(Gson().toJson(block))
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
