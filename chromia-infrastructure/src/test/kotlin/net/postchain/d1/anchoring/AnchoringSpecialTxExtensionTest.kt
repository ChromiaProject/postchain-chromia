package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AnchoringSpecialTxExtensionTest {

    lateinit var anchoringReceiverFactory: AnchoringReceiverFactory
    lateinit var sut: AnchoringSpecialTxExtension
    lateinit var module: GTXModule
    lateinit var pipe: AnchoringPipe

    var mightHaveNewPackets = true

    @BeforeEach
    fun beforeEach() {

        val blockchainRidMock = BlockchainRid.ZERO_RID

        pipe = mock<AnchoringPipe> {
            on { blockchainRid } doReturn blockchainRidMock
            on { mightHaveNewPackets() } doReturn mightHaveNewPackets
        }
        val anchoringReceiver = mock<AnchoringReceiver> {
            on { getRelevantPipes() } doReturn listOf(pipe)
        }

        anchoringReceiverFactory = mock<AnchoringReceiverFactory> {
            on { create(any(), any()) } doReturn anchoringReceiver
        }
        sut = AnchoringSpecialTxExtension(anchoringReceiverFactory)

        sut.isSigner = { true }
        sut.clusterManagement = mock<ClusterManagement>()
        sut.blockchainConfigProvider = mock<BlockchainConfigProvider>()
        sut.anchoringConfig = AnchoringBlockchainConfigData(100, 1000, 100)

        sut.createReceiver(blockchainRidMock)

        module = mock<GTXModule>()
        `when`(module.query(any(), eq("get_last_anchored_block"), any())).thenReturn(gtv(
                "block_rid" to gtv(BlockchainRid.buildRepeat(0)),
                "block_height" to gtv(0)
        ))

        sut.init(module, 0L, blockchainRidMock, mock<CryptoSystem>())
    }

    private fun generatePackets(blocks: IntRange) = blocks.map {
        AnchoringPacket(
                it.toLong(),
                GtvEncoder.encodeGtv(gtv("blockRid - %010d".format(it))),
                GtvEncoder.encodeGtv(gtv("header - %010d".format(it))),
                byteArrayOf())
    }

    @Test
    fun `read until size limit is reached`() {
        // 50 packets are available
        val packets = generatePackets(0 until 50)
        whenever(pipe.fetchNextRange(any(), any())).doReturn(packets)

        // read 3 out of 50 packets
        val sizeOf3 = packets.take(3).sumOf { sut.buildOpData(it).second }
        sut.maxTxSize = sizeOf3.toLong() + TX_SIZE_MARGIN

        val ops = sut.createSpecialOperations(SpecialTransactionPosition.Begin, mock())

        // 3 packages will fit in total size
        assertThat(ops.size).isEqualTo(3)
        verify(pipe, times(1)).fetchNextRange(any(), any())
    }
}
