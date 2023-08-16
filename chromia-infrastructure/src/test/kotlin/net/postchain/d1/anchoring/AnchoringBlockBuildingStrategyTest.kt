package net.postchain.d1.anchoring

import net.postchain.core.block.BlockQueries
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.toObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class AnchoringBlockBuildingStrategyTest {

    companion object {
        const val MAX_ANCHORING_DELAY = 1000L
        const val MAX_ANCHORING_BLOCKS_PER_ANCHOR_BLOCK = 100L
    }

    private val configData: GtvDictionary = GtvDictionary.build(mapOf(
            "max_anchoring_delay" to GtvFactory.gtv(MAX_ANCHORING_DELAY),
            "max_anchoring_blocks_per_anchor_block" to GtvFactory.gtv(MAX_ANCHORING_BLOCKS_PER_ANCHOR_BLOCK),
    ))

    private val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(-1)
        on { getLastBlockHeight() } doReturn completionStage
    }

    private var currentMillis = DynamicValueAnswer(1L)

    private val clock: Clock = mock {
        on { millis() } doAnswer currentMillis
    }

    private val txExtension: AnchoringSpecialTxExtension = mock()

    private lateinit var sut: AnchoringBlockBuildingStrategy

    @BeforeEach
    fun beforeEach() {
        currentMillis.value = 1
        sut = AnchoringBlockBuildingStrategy(GtvDictionary.build(emptyMap()).toObject(), blockQueries, mock(), clock)
        sut.txExtension = txExtension
        sut.anchoringConfig = configData.toObject()
    }

    @Test
    fun `do not use preemptive block building`() {
        assertFalse(sut.preemptiveBlockBuilding())
    }

    @Test
    fun `do not build block if TX extension is not initialized`() {
        sut = AnchoringBlockBuildingStrategy(GtvDictionary.build(emptyMap()).toObject(), blockQueries, mock(), clock)
        assertFalse(sut.extendedShouldBuildBlock())
    }

    @Test
    fun `do not build block if there is nothing to anchor`() {
        assertFalse(sut.extendedShouldBuildBlock())
    }

    @Test
    fun `do build block if below max blocks but with waiting blocks and max delay time has passed`() {
        doReturn(1L).whenever(txExtension).numberOfBlocksToAnchor()
        assertFalse(sut.extendedShouldBuildBlock()) // Initialize first time
        assertFalse(sut.extendedShouldBuildBlock())
        addTime(MAX_ANCHORING_DELAY + 1)
        assertTrue(sut.extendedShouldBuildBlock())
    }

    @Test
    fun `do build block if enough blocks needs to be anchored`() {
        doReturn(1L).whenever(txExtension).numberOfBlocksToAnchor()
        assertFalse(sut.extendedShouldBuildBlock()) // Too few
        doReturn(MAX_ANCHORING_BLOCKS_PER_ANCHOR_BLOCK).whenever(txExtension).numberOfBlocksToAnchor()
        assertTrue(sut.extendedShouldBuildBlock())
    }

    private fun addTime(millis: Long) {
        currentMillis.value = currentMillis.value + millis
    }

    class DynamicValueAnswer<T>(var value: T) : Answer<T> {
        override fun answer(p0: InvocationOnMock?): T = value
    }
}