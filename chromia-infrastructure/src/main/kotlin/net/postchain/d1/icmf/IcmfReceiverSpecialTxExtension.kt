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

    companion object : KLogging() {
        const val BLOCK_SIZE_MARGIN = 100 * 1024
    }

    private val _relevantOps = setOf(AnchorHeaderOp.OP_NAME, HeaderOp.OP_NAME, MessageHashOp.OP_NAME, MessageOp.OP_NAME)
    private lateinit var cryptoSystem: CryptoSystem
    val receivers: MutableList<GlobalTopicIcmfReceiver> = mutableListOf()
    lateinit var clusterManagement: ClusterManagement
    var maxBlockSize: Long = -1

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
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        val pipes = receivers.flatMap { it.getRelevantPipes() }

        val lastAnchoredHeights = dbOperations.loadLastAnchoredHeights(bctx).associate { (it.cluster to it.topic) to it.height }

        var currentSize = 0
        val allOps = mutableListOf<OpData>()
        var hasSpilledMessages = false
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets() && !hasSpilledMessages) {
                val clusterName = pipe.id
                val lastAnchoredHeight = lastAnchoredHeights[clusterName to pipe.route.topic] ?: -1
                var currentHeight: Long = lastAnchoredHeight
                while (pipe.mightHaveNewPackets() && !hasSpilledMessages) {
                    val icmfPackets = pipe.fetchNext(currentHeight)
                    if (icmfPackets != null) {
                        for (anchorPacket in icmfPackets.anchorPackets) {
                            if (hasSpilledMessages) break

                            val spilledMessageCounts = dbOperations.loadSpilledMessageCounts(bctx, clusterName, anchorPacket.height, pipe.route.topic)
                            if (spilledMessageCounts.isNotEmpty()) {
                                for (packet in anchorPacket.packets) {
                                    val spilledCount = spilledMessageCounts[packet.sender] ?: 0
                                    for (message in packet.messages.subList(packet.messages.size - spilledCount - 1, packet.messages.size)) {
                                        if (currentSize + message.size < maxBlockSize - BLOCK_SIZE_MARGIN) {
                                            allOps.add(MessageOp(packet.sender, packet.topic, message.body).toOpData())
                                            currentSize += message.size
                                        } else {
                                            hasSpilledMessages = true
                                        }
                                    }
                                }
                            } else {
                                allOps.add(AnchorHeaderOp(clusterName, anchorPacket.rawAnchorHeader, anchorPacket.rawAnchorWitness).toOpData())
                                for (packet in anchorPacket.packets) {
                                    val currentPrevMessageBlockHeight = dbOperations.loadLastMessageHeight(bctx, packet.sender, packet.topic)
                                    if (packet.height > currentPrevMessageBlockHeight) {
                                        allOps.add(HeaderOp(packet.rawHeader, packet.rawWitness).toOpData())

                                        for (message in packet.messages) {
                                            allOps.add(MessageHashOp(packet.sender, packet.topic, message.body.merkleHash(hashCalculator)).toOpData())
                                            if (currentSize + message.size < maxBlockSize - BLOCK_SIZE_MARGIN) {
                                                allOps.add(MessageOp(packet.sender, packet.topic, message.body).toOpData())
                                                currentSize += message.size
                                            } else {
                                                hasSpilledMessages = true
                                            }
                                        }
                                    }
                                    // else already processed in previous block, so skip it here
                                }
                                if (!hasSpilledMessages) pipe.markTaken(icmfPackets.currentPointer, bctx)
                                currentHeight = icmfPackets.currentPointer
                            }
                        }
                    } else {
                        break // Nothing more to find
                    }
                }
            }
        }
        return allOps
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
        val bodyHashesByTopic: MutableMap<String, MutableList<ByteArray>> = mutableMapOf()
        val bodyHashesBySenderAndTopic: MutableMap<Pair<BlockchainRid, String>, MutableList<ByteArray>> = mutableMapOf()
        for (op in ops) {
            when (op.opName) {
                AnchorHeaderOp.OP_NAME -> {
                    val anchorHeaderOp = AnchorHeaderOp.fromOpData(op) ?: return false

                    if (!validateHeaders(headerBlockRidsByTopic, currentAnchorHeaderData, hashCalculator, bctx)) return false
                    if (currentAnchorHeaderData != null) {
                        for (topic in headerBlockRidsByTopic.keys) {
                            dbOperations.saveLastAnchoredHeight(bctx, currentAnchorHeaderData.cluster, topic, currentAnchorHeaderData.height)
                        }
                    }
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

                    if (!validateMessages(bodyHashesByTopic, currentHeaderData, bctx)) return false
                    bodyHashesByTopic.clear()

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

                MessageHashOp.OP_NAME -> {
                    val messageHashOp = MessageHashOp.fromOpData(op) ?: return false

                    if (currentHeaderData == null) {
                        logger.warn("got ${MessageHashOp.OP_NAME} before any ${HeaderOp.OP_NAME}")
                        return false
                    }

                    val topicData = currentHeaderData.icmfHeaderData[messageHashOp.topic]
                    if (topicData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${messageHashOp.topic} for sender ${messageHashOp.sender.toHex()}")
                        return false
                    }

                    bodyHashesByTopic.computeIfAbsent(messageHashOp.topic) { mutableListOf() }
                            .add(messageHashOp.hash)

                    bodyHashesBySenderAndTopic.computeIfAbsent(messageHashOp.sender to messageHashOp.topic) { mutableListOf() }
                            .add(messageHashOp.hash)
                }

                MessageOp.OP_NAME -> {
                    val messageOp = MessageOp.fromOpData(op) ?: return false
                    val latestReceivedHashForTopic = bodyHashesBySenderAndTopic[messageOp.sender to messageOp.topic]?.removeLastOrNull()
                    val messageBodyHash = messageOp.body.merkleHash(hashCalculator)
                    if (latestReceivedHashForTopic == null) {
                        val spilledMessage = dbOperations.loadOldestSpilledMessage(bctx, messageOp.sender, messageOp.topic)
                        if (spilledMessage == null) {
                            logger.warn("Received unexpected message op for sender ${messageOp.sender} and topic ${messageOp.topic}")
                            return false
                        }

                        if (!spilledMessage.hash.contentEquals(messageBodyHash)) {
                            logger.warn("Hash of message body ${messageBodyHash.toHex()} does not match latest spilled message hash operation value ${spilledMessage.hash.toHex()} for topic ${messageOp.topic}")
                            return false
                        }
                        dbOperations.imprecateSpilledMessage(bctx, spilledMessage.serial)

                        if (dbOperations.loadSpilledMessageCounts(bctx, spilledMessage.cluster, spilledMessage.anchorHeight, messageOp.topic).isEmpty()) {
                            dbOperations.saveLastAnchoredHeight(bctx, spilledMessage.cluster, messageOp.topic, spilledMessage.anchorHeight)
                        }
                    } else {
                        if (!latestReceivedHashForTopic.contentEquals(messageBodyHash)) {
                            logger.warn("Hash of message body ${messageBodyHash.toHex()} does not match latest received message hash operation value ${latestReceivedHashForTopic.toHex()} for topic ${messageOp.topic}")
                            return false
                        }
                    }
                }

                else -> {
                    logger.warn("Got unexpected special operation: ${op.opName}")
                    return false
                }
            }
        }

        if (!validateMessages(bodyHashesByTopic, currentHeaderData, bctx) && validateHeaders(headerBlockRidsByTopic, currentAnchorHeaderData, hashCalculator, bctx)) {
            return false
        }

        if (currentAnchorHeaderData != null) {
            for ((key, hashes) in bodyHashesBySenderAndTopic) {
                val (sender, topic) = key
                hashes.forEach {
                    dbOperations.saveSpilledMessage(bctx, currentAnchorHeaderData.cluster, currentAnchorHeaderData.height, sender, topic, it)
                }
            }

            for (topic in headerBlockRidsByTopic.keys) {
                // If there is no spill we can save as last anchor height
                if (!bodyHashesBySenderAndTopic.keys.any { it.second == topic }) {
                    dbOperations.saveLastAnchoredHeight(bctx, currentAnchorHeaderData.cluster, topic, currentAnchorHeaderData.height)
                }
            }
        }

        return true
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
                if (!validatePreviousHeaderHeight(bctx, currentAnchorHeaderData.cluster, topic, data.previousBlockHeight)) return false
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
            bodyHashesByTopic: MutableMap<String, MutableList<ByteArray>>,
            currentHeaderData: HeaderValidationInfo?,
            bctx: BlockEContext
    ): Boolean {
        if (currentHeaderData != null) {
            if (!validateMessagesHash(bodyHashesByTopic, currentHeaderData)) return false
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
        } else if (bodyHashesByTopic.isNotEmpty()) {
            logger.warn("got ${MessageOp.OP_NAME} before any ${HeaderOp.OP_NAME}")
            return false
        }
        return true
    }

    private fun validatePreviousHeaderHeight(bctx: BlockEContext, cluster: String, topic: String, previousHeight: Long): Boolean {
        val currentPrevHeaderHeight = dbOperations.loadLastAnchoredHeight(bctx, cluster, topic)

        if (previousHeight != currentPrevHeaderHeight) {
            logger.warn("$ICMF_ANCHOR_HEADERS_EXTRA header extra has incorrect previous message height $previousHeight, expected $currentPrevHeaderHeight for topic $topic")
            return false
        }

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
            bodyHashesByTopic: MutableMap<String, MutableList<ByteArray>>,
            headerData: HeaderValidationInfo
    ): Boolean {
        if (headerData.icmfHeaderData.keys != bodyHashesByTopic.keys) {
            logger.warn("Header does not contain the same topics as messages received")
            return false
        }
        for ((topic, hashes) in bodyHashesByTopic) {
            val topicData = headerData.icmfHeaderData[topic]
            if (topicData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic")
                return false
            }

            val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
            val computedHash = gtv(hashes.map { gtv(it) }).merkleHash(hashCalculator)
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

    data class MessageHashOp(
            val sender: BlockchainRid,
            val topic: String,
            val hash: ByteArray
    ) {
        companion object {
            // operation __icmf_message_hash(sender: byte_array, topic: text, hash: byte_array)
            const val OP_NAME = "__icmf_message_hash"

            fun fromOpData(opData: OpData): MessageHashOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    MessageHashOp(BlockchainRid(opData.args[0].asByteArray()), opData.args[1].asString(), opData.args[2].asByteArray())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(sender), gtv(topic), gtv(hash)))
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
