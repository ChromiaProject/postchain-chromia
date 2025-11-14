package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.DynamicValueAnswer
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock

const val MAX_ANCHORING_DELAY = 1000L
const val MAX_ANCHORING_BLOCKS_PER_ANCHOR_BLOCK = 100L

class AnchoringSpecialTxExtensionTest {

    private var currentMillis = DynamicValueAnswer(1L)

    private val clock: Clock = mock {
        on { millis() } doAnswer currentMillis
    }

    lateinit var anchoringPipeManagerFactory: AnchoringPipeManagerFactory
    lateinit var sut: AnchoringSpecialTxExtension

    // anchoring chain
    private val blockchainRid0 = BlockchainRid.ZERO_RID
    private lateinit var module0: GTXModule

    // chain1
    private val brid1 = BlockchainRid.buildRepeat(1)
    private lateinit var pipe1: AnchoringPipe

    // chain2
    private val brid2 = BlockchainRid.buildRepeat(2)
    private lateinit var pipe2: AnchoringPipe

    private var mightHaveNewPackets = true

    @BeforeEach
    fun beforeEach() {

        pipe1 = mock {
            on { chainID } doReturn 1
            on { blockchainRid } doReturn brid1
            on { mightHaveNewPackets() } doReturn mightHaveNewPackets
        }

        pipe2 = mock {
            on { chainID } doReturn 2
            on { blockchainRid } doReturn brid2
            on { mightHaveNewPackets() } doReturn mightHaveNewPackets
        }

        val anchoringPipeManager: AnchoringPipeManager = mock {
            on { getRelevantPipes() } doReturn listOf(pipe1, pipe2)
        }

        anchoringPipeManagerFactory = mock {
            on { create(any(), any(), any()) } doReturn anchoringPipeManager
        }

        sut = AnchoringSpecialTxExtension(clock, anchoringPipeManagerFactory)
        sut.isSigner = { true }
        sut.clusterManagement = mock()
        sut.blockchainConfigProvider = mock {
            on { getRelevantPeers(any()) } doReturn listOf(PubKey(BlockchainRid.ZERO_RID.data))
        }
        sut.anchoringConfig = AnchoringBlockchainConfigData(100, 1000, 100, false)

        sut.createPipeManager(blockchainRid0, mock())

        module0 = mock {
            on { query(any(), eq("get_last_anchored_block"), eq(gtv("blockchain_rid" to gtv(brid1)))) } doReturn gtv(
                    "block_rid" to gtv(brid1),
                    "block_height" to gtv(0)
            )
            on { query(any(), eq("get_last_anchored_block"), eq(gtv("blockchain_rid" to gtv(brid2)))) } doReturn gtv(
                    "block_rid" to gtv(brid2),
                    "block_height" to gtv(0)
            )
        }
        sut.init(module0, 0L, blockchainRid0, mock())
    }

    private fun generatePackets(blocks: IntRange, brid: BlockchainRid) = blocks.map {
        val blockRid = GtvEncoder.encodeGtv(gtv("blockRid - %010d".format(it)))
        AnchoringPacket(
                it.toLong(),
                GtvEncoder.encodeGtv(gtv(blockRid)),
                GtvEncoder.encodeGtv(BlockHeaderData(
                        gtv(brid),
                        gtv(brid),
                        gtv(brid),
                        gtv(0),
                        gtv(it.toLong()),
                        GtvNull,
                        gtv(mapOf())
                ).toGtv()),
                byteArrayOf()
        )
    }

    @Test
    fun `read part of the first range from pipe1 until size limit is reached`() {
        // 40 packets are available
        val range0 = generatePackets(0 until 20, brid1)
        val range1 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any())).doReturn(range0, range1)

        // read 3 out of 40 packets
        val multiOpAnchoringSpecialTxBuilder = MultiOpAnchoringSpecialTxBuilder()
        val sizeOf3 = range0.take(3).sumOf { multiOpAnchoringSpecialTxBuilder.calculateRequiredSize(it) }
        sut.maxTxSize = sizeOf3.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 3 packages will fit in total size
        assertThat(ops.size).isEqualTo(3)
        verify(pipe1, times(1)).fetchNextRange(any())
        verify(pipe2, never()).fetchNextRange(any())
    }

    @Test
    fun `read first range and part of the second range from pipe1 until size limit is reached`() {
        // 40 packets are available
        val range0 = generatePackets(0 until 20, brid1)
        val range1 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any())).doReturn(range0, range1)

        // read 23 (MAX_PACKETS_PER_REQUEST + 3) out of 40 packets
        val multiOpAnchoringSpecialTxBuilder = MultiOpAnchoringSpecialTxBuilder()
        val sizeOf23 = (range0 + range1).take(23).sumOf { multiOpAnchoringSpecialTxBuilder.calculateRequiredSize(it) }
        sut.maxTxSize = sizeOf23.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 23 packages will fit in total size
        assertThat(ops.size).isEqualTo(23)
        verify(pipe1, times(2)).fetchNextRange(any())
        verify(pipe2, never()).fetchNextRange(any())
    }

    @Test
    fun `read two ranges from pipe1 and part of the first range from pipe2 until size limit is reached`() {
        // chain1 / 40 packets are available
        val range10 = generatePackets(0 until 20, brid1)
        val range11 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any())).doReturn(range10, range11, emptyList())

        // chain2 / 40 packets are available
        val range20 = generatePackets(0 until 20, brid2)
        val range21 = generatePackets(20 until 40, brid2)
        whenever(pipe2.fetchNextRange(any())).doReturn(range20, range21, emptyList())

        // read 45 packets in total
        val multiOpAnchoringSpecialTxBuilder = MultiOpAnchoringSpecialTxBuilder()
        val sizeOf45 = (range10 + range11 + range20 + range21).take(45).sumOf { multiOpAnchoringSpecialTxBuilder.calculateRequiredSize(it) }
        sut.maxTxSize = sizeOf45.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 45 packages will fit in total size
        assertThat(ops.size).isEqualTo(45)
        // 3 times b/c mightHaveNewPackets = true
        verify(pipe1, times(3)).fetchNextRange(any())
        verify(pipe2, times(1)).fetchNextRange(any())
    }

    @Test
    fun `read first range and part of the second range from pipe1 and part of the first range from pipe2 until maxBlocksPerChain is reached`() {
        // setting the maxBlocksPerChain = 33
        sut.anchoringConfig = AnchoringBlockchainConfigData(33, 1000, 100, false)

        // chain1 / 40 packets are available
        val range10 = generatePackets(0 until 20, brid1)
        val range11 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any())).doReturn(range10, range11, emptyList())

        // chain2 / 30 packets are available
        val range20 = generatePackets(0 until 20, brid2)
        val range21 = generatePackets(20 until 30, brid2)
        whenever(pipe2.fetchNextRange(any())).doReturn(range20, range21, emptyList())

        // we can read all the packages
        val multiOpAnchoringSpecialTxBuilder = MultiOpAnchoringSpecialTxBuilder()
        val sizeOf45 = (range10 + range11 + range20 + range21).sumOf { multiOpAnchoringSpecialTxBuilder.calculateRequiredSize(it) }
        sut.maxTxSize = sizeOf45.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 63 packages will fit in total size
        assertThat(ops.size).isEqualTo(33 + 30)
        verify(pipe1, times(2)).fetchNextRange(any())
        // 3 times b/c mightHaveNewPackets = true
        verify(pipe2, times(3)).fetchNextRange(any())
    }

    @Test
    fun `do not build block if there is nothing to anchor`() {
        assertFalse(sut.shouldBuildBlock())
    }

    @Test
    fun `do build block if below max blocks but with waiting blocks and max delay time has passed`() {
        whenever(pipe1.numberOfNewPackets()).doReturn(1L)
        assertFalse(sut.shouldBuildBlock()) // Initialize first time
        assertFalse(sut.shouldBuildBlock())
        addTime(MAX_ANCHORING_DELAY + 1)
        assertTrue(sut.shouldBuildBlock())
    }

    @Test
    fun `do build block if enough blocks needs to be anchored`() {
        whenever(pipe1.numberOfNewPackets()).doReturn(1L)
        assertFalse(sut.shouldBuildBlock()) // Too few
        whenever(pipe1.numberOfNewPackets()).doReturn(MAX_ANCHORING_BLOCKS_PER_ANCHOR_BLOCK)
        assertTrue(sut.shouldBuildBlock())
    }

    private fun addTime(millis: Long) {
        currentMillis.value = currentMillis.value + millis
    }
}
