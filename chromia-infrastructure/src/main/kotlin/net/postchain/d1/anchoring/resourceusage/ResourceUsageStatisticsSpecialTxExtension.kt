package net.postchain.d1.anchoring.resourceusage

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvType
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXModule
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.metrics.SUB_CONTAINER_CONTAINER_NAME_TAG
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_LEFT_MIB
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_USAGE_MIB
import net.postchain.metrics.SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE

class ResourceUsageStatisticsSpecialTxExtension : GTXSpecialTxExtension {
    lateinit var signers: List<ByteArray>
    lateinit var nodePubkey: ByteArray
    lateinit var nodePrivkey: ByteArray
    private lateinit var cs: CryptoSystem
    internal var lastSpaceUpdateTimes = mutableMapOf<String, Long>()
    private lateinit var sigMaker: SigMaker
    private var hasResourceUsageOp = false

    companion object : KLogging()

    enum class ResourceType {
        FREE_SPACE_LEFT_MIB,
        SPACE_USAGE_MIB,
        SPACE_USAGE_PERCENTAGE,
    }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val operations = mutableListOf<OpData>()

        if (::nodePubkey.isInitialized && ::nodePrivkey.isInitialized) {
            if (!::sigMaker.isInitialized) {
                sigMaker = cs.buildSigMaker(KeyPair(nodePubkey, nodePrivkey))
            }
            val containerMetricValue = getContainerMetricValues()
            if (containerMetricValue.isNotEmpty()) {
                val height = bctx.height
                val signature = sigMaker.signDigest(hash(height))
                operations.add(ValidateNodeSignatureOp(signature.subjectID, signature.data).toOpData())

                bctx.addAfterCommitHook {
                    updateContainersLastSpaceUpdateTimes(containerMetricValue)
                }
            }
            for (containerStats in containerMetricValue) {
                operations.add(ResourceUsageStatisticsOp(nodePubkey, containerStats.containerName, containerStats.measurementTime, containerStats.resourceType, containerStats.metricValue).toOpData())
            }
        }
        return operations
    }

    fun getContainerMetricValues(): List<ContainerStats> {
        try {
            val containerSpaceTimeMetrics = Metrics.globalRegistry.find(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME)
                    .gauges().associate { metric -> metric.id.tags.find { it.key == SUB_CONTAINER_CONTAINER_NAME_TAG }!!.value to metric.getLong() }

            val spaceLeftContainerStats = containerSpaceStats(SUB_CONTAINER_METRICS_SPACE_LEFT_MIB, ResourceType.FREE_SPACE_LEFT_MIB, containerSpaceTimeMetrics)
            val spaceUsageContainerStats = containerSpaceStats(SUB_CONTAINER_METRICS_SPACE_USAGE_MIB, ResourceType.SPACE_USAGE_MIB, containerSpaceTimeMetrics)
            val spaceUsagePercentageContainerStats = containerSpaceStats(SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE, ResourceType.SPACE_USAGE_PERCENTAGE, containerSpaceTimeMetrics)
            return spaceLeftContainerStats + spaceUsageContainerStats + spaceUsagePercentageContainerStats
        } catch (e: Exception) {
            logger.error("Could not read container metrics: ${e.message}", e)
        }
        return listOf()
    }

    private fun containerSpaceStats(metricName: String, resourceType: ResourceType, containerSpaceTimeMetrics: Map<String, Long>): List<ContainerStats> {
        return Metrics.globalRegistry.find(metricName).gauges()
                .mapNotNull { metric ->
                    val containerName = metric.id.tags.find { it.key == SUB_CONTAINER_CONTAINER_NAME_TAG }!!.value
                    val containerSpaceMetricTime = containerSpaceTimeMetrics[containerName] ?: 0
                    val lastSpaceUpdateTime = lastSpaceUpdateTimes.getOrDefault(containerName, 0)

                    // Discard metric if time now newer than last time
                    if (containerSpaceMetricTime > lastSpaceUpdateTime) {
                        ContainerStats(containerName, containerSpaceMetricTime, resourceType, metric.value().toLong())
                    } else null
                }
    }

    override fun getRelevantOps(): Set<String> {
        return setOf(ResourceUsageStatisticsOp.OP_NAME, ValidateNodeSignatureOp.OP_NAME)
    }

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        this.cs = cs
        this.hasResourceUsageOp = module.getOperations().contains(ResourceUsageStatisticsOp.OP_NAME)
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return when (position) {
            SpecialTransactionPosition.Begin -> this.hasResourceUsageOp
            SpecialTransactionPosition.End -> false
        }
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        if (ops.count { it.opName == ValidateNodeSignatureOp.OP_NAME } > 1) {
            logger.warn("Multiple ${ValidateNodeSignatureOp.OP_NAME} found!")
            return false
        }
        val processedResourceUsageStatisticsOpResourceType = mutableMapOf<String, MutableSet<ResourceType>>()
        var subjectID = ByteArray(0)
        for (op in ops) {
            when (op.opName) {
                ValidateNodeSignatureOp.OP_NAME -> {
                    val validateNodeSignature = ValidateNodeSignatureOp.fromOpData(op)
                            ?: return false
                    subjectID = validateNodeSignature.subjectID
                    if (!cs.verifyDigest(hash(bctx.height), Signature(subjectID, validateNodeSignature.data))) {
                        logger.warn { "Validation failed. Invalid signature" }
                        return false
                    }
                }

                ResourceUsageStatisticsOp.OP_NAME -> {
                    val opData = ResourceUsageStatisticsOp.fromOpData(op) ?: return false
                    val nodePubkey = opData.nodePubkey
                    val resourceType = opData.resourceType
                    val value = opData.metricValue
                    if (resourceType == ResourceType.FREE_SPACE_LEFT_MIB) {
                        if (value < 0) {
                            logger.warn("Invalid value: $value for free space left resource type!")
                            return false
                        }
                    }
                    if (resourceType == ResourceType.SPACE_USAGE_MIB) {
                        if (value < 0) {
                            logger.warn("Invalid value: $value for space usage mib resource type!")
                            return false
                        }
                    }
                    if (resourceType == ResourceType.SPACE_USAGE_PERCENTAGE) {
                        if (value !in 0..100) {
                            logger.warn("Invalid value: $value for space usage percentage resource type!")
                            return false
                        }
                    }
                    if (!nodePubkey.contentEquals(subjectID)) {
                        logger.warn("Signature pubkey: ${subjectID.wrap()} does not correspond to node pubkey: ${nodePubkey.wrap()} assigned to free space left resource measurement.")
                        return false
                    }
                    if (!processedResourceUsageStatisticsOpResourceType.getOrPut(opData.containerName) { mutableSetOf() }.add(resourceType)) {
                        logger.warn("Multiple ${ResourceUsageStatisticsOp.OP_NAME} operations of the same resource type detected for same container: $resourceType")
                        return false
                    }
                }

                else -> {
                    logger.warn("Got unexpected special operation: ${op.opName}")
                    return false
                }
            }
        }
        if (::signers.isInitialized) {
            if (signers.none { it.contentEquals(subjectID) }) {
                logger.warn("Signature pubkey: ${subjectID.wrap()} does not correspond to any known signers: ${signers.map { it.wrap() }}")
                return false
            }
        }
        return true
    }

    private fun hash(height: Long) = gtv(height).merkleHash(GtvMerkleHashCalculatorV2(cs))

    data class ValidateNodeSignatureOp(
            val subjectID: ByteArray,
            val data: ByteArray
    ) {
        companion object {
            // operation __resource_usage_statistics_validate_node_signature(subject_id: byte_array, data: byte_array)
            const val OP_NAME = "__resource_usage_statistics_validate_node_signature"

            val metadata = OperationMetadata(args = listOf(
                    ArgumentMetadata(name = "subject_id", gtvTypes = setOf(GtvType.BYTEARRAY)),
                    ArgumentMetadata(name = "data", gtvTypes = setOf(GtvType.BYTEARRAY))
            ))

            fun fromOpData(opData: OpData): ValidateNodeSignatureOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 2) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    ValidateNodeSignatureOp(opData.args[0].asByteArray(), opData.args[1].asByteArray())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(subjectID), gtv(data)))
    }

    data class ResourceUsageStatisticsOp(
            val nodePubkey: ByteArray,
            val containerName: String,
            val mesurementTime: Long,
            val resourceType: ResourceType,
            val metricValue: Long
    ) {
        companion object {
            //operation __resource_usage_statistics(node_pubkey: pubkey, container_name: text, mesurement_time: timestamp, resource_type: resource_type, metric_value: decimal)
            const val OP_NAME = "__resource_usage_statistics"


            fun fromOpData(opData: OpData): ResourceUsageStatisticsOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 5) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    ResourceUsageStatisticsOp(
                            opData.args[0].asByteArray(),
                            opData.args[1].asString(),
                            opData.args[2].asInteger(),
                            ResourceType.entries[opData.args[3].asInteger().toInt()],
                            opData.args[4].asString().toLong())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(nodePubkey), gtv(containerName), gtv(mesurementTime), gtv(resourceType.ordinal.toLong()), gtv(metricValue.toString())))
    }

    fun updateContainersLastSpaceUpdateTimes(containerMetricValue: List<ContainerStats>) {
        containerMetricValue
                .map { it.containerName to it.measurementTime }
                .toSet()
                .forEach {
                    lastSpaceUpdateTimes[it.first] = it.second
                }
    }
}

data class ContainerStats(val containerName: String, val measurementTime: Long, val resourceType: ResourceUsageStatisticsSpecialTxExtension.ResourceType, val metricValue: Long)

fun Meter.getLong() = measure().first().value.toLong()
