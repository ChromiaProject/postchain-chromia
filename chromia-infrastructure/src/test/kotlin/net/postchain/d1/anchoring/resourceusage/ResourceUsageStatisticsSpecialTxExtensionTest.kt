package net.postchain.d1.anchoring.resourceusage

import assertk.assertThat
import assertk.assertions.containsOnly
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.anchoring.resourceusage.ResourceUsageStatisticsSpecialTxExtension.ResourceType.FREE_SPACE_LEFT_MIB
import net.postchain.d1.anchoring.resourceusage.ResourceUsageStatisticsSpecialTxExtension.ResourceType.SPACE_USAGE_MIB
import net.postchain.d1.anchoring.resourceusage.ResourceUsageStatisticsSpecialTxExtension.ResourceType.SPACE_USAGE_PERCENTAGE
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.metrics.SUB_CONTAINER_CONTAINER_NAME_TAG
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_LEFT_MIB
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_USAGE_MIB
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class ResourceUsageStatisticsSpecialTxExtensionTest {

    @BeforeEach
    fun setUp() {
        val registries = Metrics.globalRegistry.registries.toList()
        registries.forEach(Metrics.globalRegistry::remove)

        val meters = Metrics.globalRegistry.meters.toList()
        meters.forEach(Metrics.globalRegistry::remove)

        Metrics.globalRegistry.add(SimpleMeterRegistry())
    }

    @Test
    fun getContainerMetricValues() {
        var spaceUpdateTime = 1
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            spaceUpdateTime
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)
                .apply {}

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB) {
            1000
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_USAGE_MIB) {
            10000
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE) {
            10
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()

        val containerMetricValues = resourceUsageStatisticsSpecialTxExtension.getContainerMetricValues()
        val expected = listOf(
                ContainerStats("testContainer", 1, FREE_SPACE_LEFT_MIB, 1000),
                ContainerStats("testContainer", 1, SPACE_USAGE_MIB, 10000),
                ContainerStats("testContainer", 1, SPACE_USAGE_PERCENTAGE, 10))
        assertEquals(expected, containerMetricValues)

        val emptyContainerMetricValues = resourceUsageStatisticsSpecialTxExtension.getContainerMetricValues()
        assertEquals(listOf<ContainerStats>(), emptyContainerMetricValues)

        spaceUpdateTime = 2
        val secondContainerMetricValues = resourceUsageStatisticsSpecialTxExtension.getContainerMetricValues()
        val expected2 = listOf(
                ContainerStats("testContainer", 2, FREE_SPACE_LEFT_MIB, 1000),
                ContainerStats("testContainer", 2, SPACE_USAGE_MIB, 10000),
                ContainerStats("testContainer", 2, SPACE_USAGE_PERCENTAGE, 10))
        assertEquals(expected2, secondContainerMetricValues)
    }

    @Test
    fun getContainerMetricValues_differentContainersGiveEmpty() {
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer3")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB) {
            1000
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer4")
                .register(Metrics.globalRegistry)

        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()

        val containerMetricValues = resourceUsageStatisticsSpecialTxExtension.getContainerMetricValues()
        assertEquals(listOf<ContainerStats>(), containerMetricValues)
    }

    @Test
    fun getContainerStatsMultipleContainers() {
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer1")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB) {
            1000
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer1")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            2
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer2")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB) {
            2000
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer2")
                .register(Metrics.globalRegistry)


        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()

        val containerMetricValues = resourceUsageStatisticsSpecialTxExtension.getContainerMetricValues()

        assertThat(containerMetricValues).containsOnly(
                ContainerStats("testContainer1", 1, FREE_SPACE_LEFT_MIB, 1000),
                ContainerStats("testContainer2", 2, FREE_SPACE_LEFT_MIB, 2000)
        )
    }

    @Test
    fun validateSpecialOperations_validateNodeSignature() {
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB) {
            1000
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        val cs = Secp256K1CryptoSystem()
        val keyPair = cs.generateKeyPair()

        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
        resourceUsageStatisticsSpecialTxExtension.init(mock(), 3, mock(), cs)
        resourceUsageStatisticsSpecialTxExtension.nodePrivkey = keyPair.privKey.data
        resourceUsageStatisticsSpecialTxExtension.nodePubkey = keyPair.pubKey.data

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = resourceUsageStatisticsSpecialTxExtension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertTrue(validateSpecialOperations)
    }

    @Test
    fun validateSpecialOperations_invalidateNodeSignature() {
        val cs = Secp256K1CryptoSystem()
        val nodeKeypair = cs.generateKeyPair()
        val sigMaker = cs.buildSigMaker(nodeKeypair)
        val signature = sigMaker.signDigest(GtvFactory.gtv(10).merkleHash(GtvMerkleHashCalculatorV2(cs)))

        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
        resourceUsageStatisticsSpecialTxExtension.init(mock(), 3, mock(), cs)

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val differentNodeKeypair = cs.generateKeyPair()
        val specialOperationsInvalidNodeSignature = listOf(
                ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp(signature.subjectID, signature.data).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(differentNodeKeypair.pubKey.data, "testContainer", 1, FREE_SPACE_LEFT_MIB, 1000).toOpData())
        assertFalse(resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperationsInvalidNodeSignature))
    }


    @Test
    fun validateSpecialOperations_invalidFreeSpaceLeft() {
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB) {
            -1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        val resourceUsageStatisticsSpecialTxExtension = resourceUsageStatisticsSpecialTxExtension()

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = resourceUsageStatisticsSpecialTxExtension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertFalse(validateSpecialOperations)
    }

    @Test
    fun validateSpecialOperations_invalidSpaceUsage() {
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_USAGE_MIB) {
            -1
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        val resourceUsageStatisticsSpecialTxExtension = resourceUsageStatisticsSpecialTxExtension()

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = resourceUsageStatisticsSpecialTxExtension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertFalse(validateSpecialOperations)
    }

    @Test
    fun validateSpecialOperations_invalidSpaceUsagePercentage() {
        var spaceUpdateTime = 1
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME) {
            spaceUpdateTime
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        var spaceUsagePercentage = -1
        Gauge.builder(SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE) {
            spaceUsagePercentage
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, "testContainer")
                .register(Metrics.globalRegistry)

        val resourceUsageStatisticsSpecialTxExtension = resourceUsageStatisticsSpecialTxExtension()

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = resourceUsageStatisticsSpecialTxExtension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertFalse(validateSpecialOperations)

        spaceUpdateTime = 2
        spaceUsagePercentage = 101

        val specialOperations2 = resourceUsageStatisticsSpecialTxExtension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)

        val validateSpecialOperations2 = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations2)

        assertFalse(validateSpecialOperations2)
    }

    @Test
    fun validateSpecialOperations_duplicate_operation_resource_type() {
        val cs = spy(Secp256K1CryptoSystem())
        val keyPair = cs.generateKeyPair()
        doReturn(true).`when`(cs).verifyDigest(any(), any())
        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
        resourceUsageStatisticsSpecialTxExtension.init(mock(), 3, mock(), cs)

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = listOf(ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp(keyPair.pubKey.data, ByteArray(0)).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(keyPair.pubKey.data, "testContainer", 1, FREE_SPACE_LEFT_MIB, 1000).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(keyPair.pubKey.data, "testContainer", 1, FREE_SPACE_LEFT_MIB, 1000).toOpData())

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertFalse(validateSpecialOperations)
    }

    @Test
    fun validateSpecialOperations_different_operation_resource_type() {
        val cs = spy(Secp256K1CryptoSystem())
        val keyPair = cs.generateKeyPair()
        doReturn(true).`when`(cs).verifyDigest(any(), any())
        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
        resourceUsageStatisticsSpecialTxExtension.init(mock(), 3, mock(), cs)

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = listOf(ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp(keyPair.pubKey.data, ByteArray(0)).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(keyPair.pubKey.data, "testContainer", 1, FREE_SPACE_LEFT_MIB, 1000).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(keyPair.pubKey.data, "testContainer", 1, SPACE_USAGE_MIB, 10000).toOpData())

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertTrue(validateSpecialOperations)
    }

    @Test
    fun validateSpecialOperations_unknownSigner() {
        val cs = spy(Secp256K1CryptoSystem())
        val keyPair = cs.generateKeyPair()
        doReturn(true).`when`(cs).verifyDigest(any(), any())
        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
        resourceUsageStatisticsSpecialTxExtension.init(mock(), 3, mock(), cs)
        resourceUsageStatisticsSpecialTxExtension.signers = listOf(ByteArray(20))

        val bctx = mock<BlockEContext>()
        `when`(bctx.height).thenReturn(10)
        val specialOperations = listOf(ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp(keyPair.pubKey.data, ByteArray(0)).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(keyPair.pubKey.data, "testContainer", 1, FREE_SPACE_LEFT_MIB, 1000).toOpData())

        val validateSpecialOperations = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations)

        assertFalse(validateSpecialOperations)

        val specialOperations2 = listOf(ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp(ByteArray(20), ByteArray(0)).toOpData(),
                ResourceUsageStatisticsSpecialTxExtension.ResourceUsageStatisticsOp(ByteArray(20), "testContainer", 1, FREE_SPACE_LEFT_MIB, 1000).toOpData())

        val validateSpecialOperations2 = resourceUsageStatisticsSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, specialOperations2)

        assertTrue(validateSpecialOperations2)
    }

    private fun resourceUsageStatisticsSpecialTxExtension(): ResourceUsageStatisticsSpecialTxExtension {
        val cs = Secp256K1CryptoSystem()
        val keyPair = cs.generateKeyPair()

        val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
        resourceUsageStatisticsSpecialTxExtension.init(mock(), 3, mock(), cs)
        resourceUsageStatisticsSpecialTxExtension.nodePrivkey = keyPair.privKey.data
        resourceUsageStatisticsSpecialTxExtension.nodePubkey = keyPair.pubKey.data
        return resourceUsageStatisticsSpecialTxExtension
    }
}