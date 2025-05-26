package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.createLogCaptor
import net.postchain.core.block.BlockQueries
import net.postchain.d1.anchoring.AnchoringPipe.Companion.MAX_PACKETS_PER_REQUEST
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.network.mastersub.MasterSubQueryManager
import net.postchain.network.mastersub.master.MasterConnectionManager
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AnchoringSubnodePipeTest {

    private val executor = Executors.newScheduledThreadPool(2)
    private val blocks = ConcurrentSkipListMap<Long, DatabaseAccess.BlockInfoExt>()
    private val height = AtomicLong(0)
    private val anchoredHeight = AtomicLong(-1)
    private val maxBlockTime = 100L // ms
    private val anchoringMaxBlockTime = 3000L // ms

    @BeforeEach
    fun setUp() {
        height.set(0)
        anchoredHeight.set(-1)

        // block builder
        executor.scheduleAtFixedRate({
            val currentHeight = height.getAndIncrement()
            blocks[currentHeight] = DatabaseAccess.BlockInfoExt(
                    byteArrayOf(),
                    currentHeight,
                    byteArrayOf(),
                    byteArrayOf(),
                    currentHeight
            )
        }, 0, maxBlockTime, TimeUnit.MILLISECONDS)
    }

    @AfterEach
    fun tearDown() {
        executor.shutdown()
    }

    @Test
    fun `pipe fetches all blocks in reasonable time`() {
        val queryManager: MasterSubQueryManager = mock {
            on { blocksFromHeight(any(), any(), anyLong()) } doAnswer { invocation ->
                val fromHeight = invocation.getArgument<Long>(1)
                CompletableFuture.completedFuture(
                        blocks.tailMap(fromHeight, true).values.take(MAX_PACKETS_PER_REQUEST.toInt()).toList()
                )
            }
        }

        val connectionManager: MasterConnectionManager = mock {
            on { masterSubQueryManager } doReturn queryManager
        }

        val blockQueries: BlockQueries = mock {
            on { query(eq("get_last_anchored_block"), any()) } doReturn CompletableFuture.completedFuture(
                    gtv(mapOf("block_height" to gtv(-1L)))
            )
        }

        val sut = AnchoringSubnodePipe(0, BlockchainRid.ZERO_RID, connectionManager, { blockQueries })

        // afterCommit handler
        executor.scheduleAtFixedRate({
            sut.newBlockAvailable(height.get())
        }, 0, maxBlockTime, TimeUnit.MILLISECONDS)

        // anchoring
        executor.scheduleAtFixedRate({
            val anchored = sut.fetchNextRange(anchoredHeight.get())
            anchoredHeight.set(anchored.last().height)
        }, anchoringMaxBlockTime, anchoringMaxBlockTime, TimeUnit.MILLISECONDS)

        // anchor 100 blocks in a reasonable time
        await().atMost(Duration.ONE_MINUTE).until {
            anchoredHeight.get() >= 100
        }
    }

    @Test
    fun `pipe stops fetching blocks when fetch limit is reached`() {
        val appender = createLogCaptor(AnchoringSubnodePipe::class.java, "List")

        val queryManager: MasterSubQueryManager = mock {
            on { blocksFromHeight(any(), any(), anyLong()) } doAnswer { invocation ->
                val fromHeight = invocation.getArgument<Long>(1)
                CompletableFuture.completedFuture(
                        blocks.tailMap(fromHeight, true).values.take(MAX_PACKETS_PER_REQUEST.toInt()).toList()
                )
            }
        }

        val connectionManager: MasterConnectionManager = mock {
            on { masterSubQueryManager } doReturn queryManager
        }

        val blockQueries: BlockQueries = mock {
            on { query(eq("get_last_anchored_block"), any()) } doReturn CompletableFuture.completedFuture(
                    gtv(mapOf("block_height" to gtv(-1L)))
            )
        }

        val sut = AnchoringSubnodePipe(0, BlockchainRid.ZERO_RID, connectionManager, { blockQueries }, 20)

        executor.scheduleAtFixedRate({
            sut.newBlockAvailable(height.get())
        }, 0, maxBlockTime, TimeUnit.MILLISECONDS)

        // Wait for 30 blocks
        Thread.sleep(maxBlockTime * 30)

        // Assert that we did not fetch more than 20 blocks
        assertThat(sut.fetchNextRange(0).size).isEqualTo(20)
        // Assert that we have the skip log
        assertThat(appender.events.map { it.message.toString() }).contains("Reached the limit of 20 unprocessed fetched blocks, skipping fetch")
    }
}