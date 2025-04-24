package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.block.BlockQueries
import net.postchain.d1.anchoring.cluster.ClusterAnchoringReceiver
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.network.mastersub.master.MasterConnectionManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class AnchoringDispatcherTest {

    @Test
    fun `initialize dispatcher`() {
        val sut = AnchoringDispatcher(mock(), mock())
        sut.initializeClusterManagementIfNotSet(mock())
        // Initialization can be done multiple times, only the first takes effect
        sut.initializeClusterManagementIfNotSet(mock())
    }

    @Test
    fun `can connect local chain without cluster management initialization`() {
        val sut = AnchoringDispatcher(mock(), mock())
        val receiver = mock<AnchoringReceiver>() {
            on { cluster } doReturn "cluster1"
            on { localPipes } doReturn ConcurrentHashMap()
        }
        sut.connectReceiver(1L, receiver, mock())
        sut.connectChain(100L, mock())
        assertThat(receiver.localPipes.keys).contains(100L)
    }

    @Test
    fun `cannot connect subnode chain without cluster management initialization`() {
        val sut = AnchoringDispatcher(mock(), mock())
        val receiver = mock<AnchoringReceiver>() {
            on { cluster } doReturn "cluster1"
            on { localPipes } doReturn ConcurrentHashMap()
        }
        sut.connectReceiver(1L, receiver, mock())
        assertThrows<UninitializedPropertyAccessException> {
            sut.connectSubnodeChain(100L, mock())
        }
        assertThat(receiver.localPipes.keys).isEmpty()
    }

    @Test
    fun `connect two dapp clusters, each with multiple chains`() {
        // Chains
        val chain111 = BlockchainRid.buildRepeat(111)
        val chain112 = BlockchainRid.buildRepeat(112)
        val chain121 = BlockchainRid.buildRepeat(121)
        val chain122 = BlockchainRid.buildRepeat(122)
        val chain127 = BlockchainRid.buildRepeat(127)

        // Mocks
        val connManager = mock<MasterConnectionManager> {
            on { masterSubQueryManager } doReturn mock()
        }
        val infra = mock<MasterBlockchainInfra> {
            on { masterConnectionManager } doReturn connManager
        }
        val sut = AnchoringDispatcher(mock(), infra)

        val clusterManagement = mock<ClusterManagement>() {
            on { getClusterOfBlockchain(chain111) } doReturn "cluster1"
            on { getClusterOfBlockchain(chain112) } doReturn "cluster1"
            on { getClusterOfBlockchain(chain121) } doReturn "cluster2"
            on { getClusterOfBlockchain(chain122) } doReturn "cluster2"
            // The chain127 is moving from cluster1 to cluster2
            on { getClusterOfBlockchain(chain127) } doReturnConsecutively listOf("cluster1", "cluster2")
            on { getActiveBlockchains(eq("cluster1")) } doReturn listOf(chain111, chain112)
            on { getActiveBlockchains(eq("cluster2")) } doReturn listOf(chain121, chain122, chain127)
        }

        val receiver1: AnchoringReceiver = spy(ClusterAnchoringReceiver("cluster1", null, clusterManagement))
        val receiver2: AnchoringReceiver = spy(ClusterAnchoringReceiver("cluster2", null, clusterManagement))

        // chain127 has 127 anchored blocks in the cluster1 and only 7 anchored blocks in the cluster2
        val anchoringBlockQueries1 = buildBlockQueriesMock(chain111 to 111L, chain112 to 112L, chain127 to 127L)
        val anchoringBlockQueries2 = buildBlockQueriesMock(chain121 to 121L, chain122 to 122L, chain127 to 7L)

        // Initialize cluster management
        sut.initializeClusterManagementIfNotSet(clusterManagement)

        // Connect chains in mixed order
        //  - connect CAC1
        sut.connectReceiver(1L, receiver1, anchoringBlockQueries1)
        //  - connect chain121 from cluster2
        sut.connectSubnodeChain(121L, chain121)
        //  - connect chain111 and chain112 from cluster1
        sut.connectSubnodeChain(111L, chain111)
        sut.connectSubnodeChain(112L, BlockchainRid.buildRepeat(112))
        //  - connect CAC2
        sut.connectReceiver(2L, receiver2, anchoringBlockQueries2)
        //  - connect chain122 from cluster2
        sut.connectSubnodeChain(122L, chain122)
        //  - connect chain127 from cluster1 and cluster2 (chain127 is moving from cluster1 to cluster2)
        sut.connectSubnodeChain(127L, chain127)
        sut.connectSubnodeChain(127L, chain127)

        // Verification
        assertThat(receiver1.localPipes.keys).containsAll(111L, 112L, 121L, 122L, 127L)
        assertThat(receiver1.getRelevantPipes().map { it.chainID }).containsAll(111L, 112L)
        assertThat(getLastAnchoringHeight(receiver1.localPipes[111L])).isEqualTo(111L)
        assertThat(getLastAnchoringHeight(receiver1.localPipes[112L])).isEqualTo(112L)
        // chain127 has 127 anchored blocks in the cluster1
        assertThat(getLastAnchoringHeight(receiver1.localPipes[127L])).isEqualTo(127L)

        assertThat(receiver2.localPipes.keys).containsAll(111L, 112L, 121L, 122L, 127L)
        assertThat(receiver2.getRelevantPipes().map { it.chainID }).containsAll(121L, 122L, 127L)
        assertThat(getLastAnchoringHeight(receiver2.localPipes[121L])).isEqualTo(121L)
        assertThat(getLastAnchoringHeight(receiver2.localPipes[122L])).isEqualTo(122L)
        // chain127 has only 7 anchored blocks in the cluster2
        assertThat(getLastAnchoringHeight(receiver2.localPipes[127L])).isEqualTo(7L)
    }

    private fun buildBlockQueriesMock(chain1: Pair<BlockchainRid, Long>, chain2: Pair<BlockchainRid, Long>, chain3: Pair<BlockchainRid, Long>) = mock<BlockQueries> {
        on {
            query(
                    eq("get_last_anchored_block"),
                    eq(gtv(mapOf("blockchain_rid" to gtv(chain1.first))))
            )
        } doReturn CompletableFuture.completedFuture(gtv(mapOf("block_height" to GtvInteger(chain1.second))))

        on {
            query(
                    eq("get_last_anchored_block"),
                    eq(gtv(mapOf("blockchain_rid" to gtv(chain2.first))))
            )
        } doReturn CompletableFuture.completedFuture(gtv(mapOf("block_height" to GtvInteger(chain2.second))))

        on {
            query(
                    eq("get_last_anchored_block"),
                    eq(gtv(mapOf("blockchain_rid" to gtv(chain3.first))))
            )
        } doReturn CompletableFuture.completedFuture(gtv(mapOf("block_height" to GtvInteger(chain3.second))))
    }

    private fun getLastAnchoringHeight(pipe: AnchoringPipe?): Long {
        return (pipe as? AnchoringSubnodePipe)?.anchorBlockQueriesProvider()
                ?.query("get_last_anchored_block", gtv(mapOf("blockchain_rid" to gtv(pipe.blockchainRid))))?.get()
                ?.let { ((it as? GtvDictionary)?.dict?.get("block_height") as? GtvInteger)?.integer }
                ?: -1L
    }

}