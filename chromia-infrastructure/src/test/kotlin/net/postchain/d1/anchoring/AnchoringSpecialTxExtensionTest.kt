package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AnchoringSpecialTxExtensionTest {

    lateinit var anchoringReceiverFactory: AnchoringReceiverFactory
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

        val anchoringReceiver: AnchoringReceiver = mock {
            on { getRelevantPipes() } doReturn listOf(pipe1, pipe2)
        }

        anchoringReceiverFactory = mock {
            on { create(any(), any()) } doReturn anchoringReceiver
        }

        sut = AnchoringSpecialTxExtension(anchoringReceiverFactory)
        sut.isSigner = { true }
        sut.clusterManagement = mock()
        sut.blockchainConfigProvider = mock()
        sut.anchoringConfig = AnchoringBlockchainConfigData(100, 1000, 100)

        sut.createReceiver(blockchainRid0)

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
        AnchoringPacket(
                it.toLong(),
                GtvEncoder.encodeGtv(gtv("blockRid - %010d".format(it))),
                GtvEncoder.encodeGtv(gtv("header - $brid - %010d".format(it))),
                byteArrayOf())
    }

    @Test
    fun `read a few packets from the first range from pipe1 until size limit is reached`() {
        // 40 packets are available
        val range0 = generatePackets(0 until 20, brid1)
        val range1 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any(), any())).doReturn(range0, range1)

        // read 3 out of 40 packets
        val sizeOf3 = range0.take(3).sumOf { sut.buildOpData(it).second }
        sut.maxTxSize = sizeOf3.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 3 packages will fit in total size
        assertThat(ops.size).isEqualTo(3)
        verify(pipe1, times(1)).fetchNextRange(any(), any())
        verify(pipe2, never()).fetchNextRange(any(), any())
    }

    @Test
    fun `read entire first range and part of second range from pipe1 until size limit is reached`() {
        // 40 packets are available
        val range0 = generatePackets(0 until 20, brid1)
        val range1 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any(), any())).doReturn(range0, range1)

        // read 23 (MAX_PACKETS_PER_REQUEST + 3) out of 40 packets
        val sizeOf23 = (range0 + range1).take(23).sumOf { sut.buildOpData(it).second }
        sut.maxTxSize = sizeOf23.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 23 packages will fit in total size
        assertThat(ops.size).isEqualTo(23)
        verify(pipe1, times(2)).fetchNextRange(any(), any())
        verify(pipe2, never()).fetchNextRange(any(), any())
    }

    @Test
    fun `read two ranges from pipe1 and part of the first range from pipe2 until size limit is reached`() {
        // chain1 / 40 packets are available
        val range10 = generatePackets(0 until 20, brid1)
        val range11 = generatePackets(20 until 40, brid1)
        whenever(pipe1.fetchNextRange(any(), any())).doReturn(range10, range11, emptyList())

        // chain2 / 40 packets are available
        val range20 = generatePackets(0 until 20, brid2)
        val range21 = generatePackets(20 until 40, brid2)
        whenever(pipe2.fetchNextRange(any(), any())).doReturn(range20, range21, emptyList())

        // read 45 packets in total
        val sizeOf45 = (range10 + range11 + range20 + range21).take(45).sumOf { sut.buildOpData(it).second }
        sut.maxTxSize = sizeOf45.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 23 packages will fit in total size
        assertThat(ops.size).isEqualTo(45)
        // 3 times b/c mightHaveNewPackets = true
        verify(pipe1, times(3)).fetchNextRange(any(), any())
        verify(pipe2, times(1)).fetchNextRange(any(), any())
    }
}
