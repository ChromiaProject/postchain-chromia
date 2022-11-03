package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchor.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfReceiverSpecialTxExtension(private val dbOperations: IcmfDatabaseOperations) : GTXSpecialTxExtension {

    companion object : KLogging()

    private val _relevantOps = setOf(AnchorHeaderOp.OP_NAME, HeaderOp.OP_NAME, MessageOp.OP_NAME)
    private lateinit var cryptoSystem: CryptoSystem
    val receivers: MutableList<GlobalTopicIcmfReceiver> = mutableListOf()
    lateinit var clusterManagement: ClusterManagement

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        cryptoSystem = cs
    }

    override fun getRelevantOps() = _relevantOps

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean = when (position) {
        SpecialTransactionPosition.Begin -> true
        SpecialTransactionPosition.End -> false
    }

    /**
     * I am block builder, go fetch messages.
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val pipes = receivers.flatMap { it.getRelevantPipes() }

        val lastAnchoredHeights = dbOperations.loadLastAnchoredHeights(bctx).associate { (it.cluster to it.topic) to it.height }

        val allOps = mutableListOf<OpData>()
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets()) {
                val clusterName = pipe.id
                val lastAnchoredHeight = lastAnchoredHeights[clusterName to pipe.route.topic] ?: -1
                var currentHeight: Long = lastAnchoredHeight
                while (pipe.mightHaveNewPackets()) {
                    val icmfPackets = pipe.fetchNext(currentHeight)
                    if (icmfPackets != null) {
                        for (anchorPacket in icmfPackets.anchorPackets) {
                            allOps.add(AnchorHeaderOp(clusterName, anchorPacket.rawAnchorHeader, anchorPacket.rawAnchorWitness).toOpData())
                            for (packet in anchorPacket.packets) {
                                val currentPrevMessageBlockHeight = dbOperations.loadLastMessageHeight(bctx, packet.sender, packet.topic)
                                if (packet.height > currentPrevMessageBlockHeight) {
                                    allOps.addAll(buildOpData(packet))
                                }
                                // else already processed in previous block, so skip it here
                            }
                            pipe.markTaken(icmfPackets.currentPointer, bctx)
                            currentHeight = icmfPackets.currentPointer
                        }
                    } else {
                        break // Nothing more to find
                    }
                }
            }
        }
        return allOps
    }

    private fun buildOpData(icmfPacket: IcmfPacket): List<OpData> {
        val operations = mutableListOf<OpData>()
        operations.add(HeaderOp(icmfPacket.rawHeader, icmfPacket.rawWitness).toOpData())

        for (body in icmfPacket.bodies) {
            operations.add(MessageOp(icmfPacket.sender, icmfPacket.topic, body).toOpData())
        }
        return operations
    }

    /**
     * I am validator, validate messages.
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        var currentAnchorHeaderData: AnchorHeaderValidationInfo? = null
        val headerBlockRidsByTopic: MutableMap<String, MutableList<ByteArray>> = mutableMapOf()
        var currentHeaderData: HeaderValidationInfo? = null
        val bodiesByTopic: MutableMap<String, MutableList<Gtv>> = mutableMapOf()
        for (op in ops) {
            when (op.opName) {
                AnchorHeaderOp.OP_NAME -> {
                    val anchorHeaderOp = AnchorHeaderOp.fromOpData(op) ?: return false

                    if (!validateHeaders(headerBlockRidsByTopic, currentAnchorHeaderData, hashCalculator, bctx)) return false
                    headerBlockRidsByTopic.clear()

                    val decodedHeader = BlockHeaderData.fromBinary(anchorHeaderOp.rawHeader)
                    val blockRid = decodedHeader.toGtv().merkleHash(hashCalculator)

                    val anchorHeaderData = TopicHeaderData.extractTopicHeaderData(decodedHeader, anchorHeaderOp.rawHeader, anchorHeaderOp.rawWitness, blockRid, cryptoSystem, clusterManagement, ICMF_ANCHOR_HEADERS_EXTRA)
                            ?: return false

                    currentAnchorHeaderData = AnchorHeaderValidationInfo(
                            decodedHeader.getHeight(),
                            anchorHeaderOp.cluster,
                            anchorHeaderData
                    )
                }

                HeaderOp.OP_NAME -> {
                    val headerOp = HeaderOp.fromOpData(op) ?: return false

                    if (!validateMessages(bodiesByTopic, currentHeaderData, bctx)) return false
                    bodiesByTopic.clear()

                    val decodedHeader = BlockHeaderData.fromBinary(headerOp.rawHeader)
                    val blockRid = decodedHeader.toGtv().merkleHash(hashCalculator)
                    val topicData = TopicHeaderData.extractTopicHeaderData(decodedHeader, headerOp.rawHeader, headerOp.rawWitness, blockRid, cryptoSystem, clusterManagement, ICMF_BLOCK_HEADER_EXTRA)
                            ?: return false

                    topicData.keys.forEach {
                        headerBlockRidsByTopic.computeIfAbsent(it) { mutableListOf() }
                                .add(blockRid)
                    }
                    currentHeaderData = HeaderValidationInfo(
                            decodedHeader.getHeight(),
                            decodedHeader.getBlockchainRid(),
                            topicData
                    )
                }

                MessageOp.OP_NAME -> {
                    val messageOp = MessageOp.fromOpData(op) ?: return false

                    if (currentHeaderData == null) {
                        logger.warn("got ${MessageOp.OP_NAME} before any ${HeaderOp.OP_NAME}")
                        return false
                    }

                    val topicData = currentHeaderData.icmfHeaderData[messageOp.topic]
                    if (topicData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $messageOp.topic for sender ${messageOp.sender.toHex()}")
                        return false
                    }

                    bodiesByTopic.computeIfAbsent(messageOp.topic) { mutableListOf() }
                            .add(messageOp.body)
                }

                else -> {
                    logger.warn("Got unexpected special operation: ${op.opName}")
                    return false
                }
            }
        }
        return validateMessages(bodiesByTopic, currentHeaderData, bctx) && validateHeaders(headerBlockRidsByTopic, currentAnchorHeaderData, hashCalculator, bctx)
    }

    private fun validateHeaders(
            headerBlockRids: Map<String, MutableList<ByteArray>>,
            currentAnchorHeaderData: AnchorHeaderValidationInfo?,
            hashCalculator: GtvMerkleHashCalculator,
            bctx: BlockEContext
    ): Boolean {
        if (currentAnchorHeaderData != null && headerBlockRids.isNotEmpty()) {
            if (currentAnchorHeaderData.anchorHeaderData.keys != headerBlockRids.keys) {
                logger.warn("Anchor header does not contain the same topics as received header messages")
            }

            for ((topic, data) in currentAnchorHeaderData.anchorHeaderData) {
                if (!validatePreviousHeaderHeight(bctx, currentAnchorHeaderData.cluster, topic, data.previousBlockHeight, currentAnchorHeaderData.height)) return false
            }

            for ((topic, blockRids) in headerBlockRids) {
                val hash = gtv(blockRids.map { gtv(it) }).merkleHash(hashCalculator)

                val anchorHeaderData = currentAnchorHeaderData.anchorHeaderData[topic]
                if (anchorHeaderData == null) {
                    logger.warn("$ICMF_ANCHOR_HEADERS_EXTRA missing data for topic $topic")
                    return false
                }

                if (!hash.contentEquals(anchorHeaderData.hash)) {
                    logger.warn("Invalid block-rid hash, expected ${anchorHeaderData.hash.toHex()} but was ${hash.toHex()}")
                    return false
                }
            }
        } else if (headerBlockRids.isNotEmpty()) {
            logger.warn("got ${HeaderOp.OP_NAME} before any ${AnchorHeaderOp.OP_NAME}")
            return false
        }
        return true
    }

    private fun validateMessages(
            bodiesByTopic: MutableMap<String, MutableList<Gtv>>,
            currentHeaderData: HeaderValidationInfo?,
            bctx: BlockEContext
    ): Boolean {
        if (currentHeaderData != null) {
            if (!validateMessagesHash(bodiesByTopic, currentHeaderData)) return false
            for ((topic, data) in currentHeaderData.icmfHeaderData) {
                if (!validatePrevMessageHeight(
                                bctx,
                                currentHeaderData.sender,
                                topic,
                                data.previousBlockHeight,
                                currentHeaderData.height
                        )
                ) return false
            }
        } else if (bodiesByTopic.isNotEmpty()) {
            logger.warn("got ${MessageOp.OP_NAME} before any ${HeaderOp.OP_NAME}")
            return false
        }
        return true
    }

    private fun validatePreviousHeaderHeight(bctx: BlockEContext, cluster: String, topic: String, previousHeight: Long, height: Long): Boolean {
        val currentPrevHeaderHeight = dbOperations.loadLastAnchoredHeight(bctx, cluster, topic)

        if (previousHeight != currentPrevHeaderHeight) {
            logger.warn("$ICMF_ANCHOR_HEADERS_EXTRA header extra has incorrect previous message height $previousHeight, expected $currentPrevHeaderHeight for topic $topic")
            return false
        }

        dbOperations.saveLastAnchoredHeight(bctx, cluster, topic, height)

        return true
    }

    private fun validatePrevMessageHeight(
            bctx: BlockEContext,
            sender: ByteArray,
            topic: String,
            prevMessageBlockHeight: Long,
            height: Long
    ): Boolean {
        val currentPrevMessageBlockHeight = dbOperations.loadLastMessageHeight(bctx, BlockchainRid(sender), topic)

        if (prevMessageBlockHeight != currentPrevMessageBlockHeight) {
            logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height $prevMessageBlockHeight, expected $currentPrevMessageBlockHeight for topic $topic for sender ${sender.toHex()}")
            return false
        }

        dbOperations.saveLastMessageHeight(bctx, BlockchainRid(sender), topic, height)

        return true
    }

    private fun validateMessagesHash(
            bodiesByTopic: MutableMap<String, MutableList<Gtv>>,
            headerData: HeaderValidationInfo
    ): Boolean {
        if (headerData.icmfHeaderData.keys != bodiesByTopic.keys) {
            logger.warn("Header does not contain the same topics as messages received")
            return false
        }
        for ((topic, bodies) in bodiesByTopic) {
            val topicData = headerData.icmfHeaderData[topic]
            if (topicData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic")
                return false
            }

            val computedHash = gtv(bodies).merkleHash(GtvMerkleHashCalculator(cryptoSystem))
            if (!topicData.hash.contentEquals(computedHash)) {
                logger.warn("invalid messages hash for topic: $topic")
                return false
            }
        }
        return true
    }

    data class AnchorHeaderValidationInfo(
            val height: Long,
            val cluster: String,
            val anchorHeaderData: Map<String, TopicHeaderData>
    )

    data class HeaderValidationInfo(
            val height: Long,
            val sender: ByteArray,
            val icmfHeaderData: Map<String, TopicHeaderData>
    )

    data class AnchorHeaderOp(
            val cluster: String,
            val rawHeader: ByteArray,
            val rawWitness: ByteArray
    ) {
        companion object {
            // operation __icmf_anchor_header(cluster: text, block_header: byte_array, witness: byte_array)
            const val OP_NAME = "__icmf_anchor_header"

            fun fromOpData(opData: OpData): AnchorHeaderOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    AnchorHeaderOp(opData.args[0].asString(), opData.args[1].asByteArray(), opData.args[2].asByteArray())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(cluster), gtv(rawHeader), gtv(rawWitness)))
    }

    data class HeaderOp(
            val rawHeader: ByteArray,
            val rawWitness: ByteArray
    ) {
        companion object {
            // operation __icmf_header(block_header: byte_array, witness: byte_array)
            const val OP_NAME = "__icmf_header"

            fun fromOpData(opData: OpData): HeaderOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 2) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    HeaderOp(opData.args[0].asByteArray(), opData.args[1].asByteArray())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(rawHeader), gtv(rawWitness)))
    }

    data class MessageOp(
            val sender: BlockchainRid,
            val topic: String,
            val body: Gtv
    ) {
        companion object {
            // operation __icmf_message(sender: byte_array, topic: text, body: gtv)
            const val OP_NAME = "__icmf_message"

            fun fromOpData(opData: OpData): MessageOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    MessageOp(BlockchainRid(opData.args[0].asByteArray()), opData.args[1].asString(), opData.args[2])
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(sender), gtv(topic), body))
    }
}
