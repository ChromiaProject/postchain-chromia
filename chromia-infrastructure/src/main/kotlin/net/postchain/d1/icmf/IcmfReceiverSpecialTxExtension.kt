package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.extension.getMerkleHashVersion
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.block.BlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.getCachedPeers
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvType
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXModule
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXBlockBuildingAffectingSpecialTxExtension
import java.time.Clock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class IcmfReceiverSpecialTxExtension(private val dbOperations: IcmfReceiverDatabaseOperations, private val clock: Clock = Clock.systemUTC()) : GTXBlockBuildingAffectingSpecialTxExtension {

    companion object : KLogging() {
        val BASE_SPECIAL_TX_OVERHEAD = Gtx(
                GtxBody(BlockchainRid.ZERO_RID, emptyArray(), emptyArray()),
                emptyList()
        ).encode().size
    }

    val anchoredReceivers: MutableList<IcmfReceiver<TopicRoute, Long, IcmfAnchorPacket, String>> = mutableListOf()
    val nonAnchoredReceivers: MutableList<IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>> = mutableListOf()
    lateinit var blockchainConfigProvider: BlockchainConfigProvider
    lateinit var clusterManagement: ClusterManagement
    lateinit var isSigner: () -> Boolean
    lateinit var icmfReceiverBlockchainConfigData: IcmfReceiverBlockchainConfigData
    var maxTxSize: Long = -1
    var specialTxSizeMargin = -1L
    lateinit var directoryChainBrid: BlockchainRid
    lateinit var initialDappProvidedTopics: List<IcmfReceiverEventTopic>

    private val _relevantOps = setOf(
            AnchorHeaderOp.OP_NAME,
            AnchoredHeaderOp.OP_NAME,
            NonAnchoredHeaderOp.OP_NAME,
            MessageHashOp.OP_NAME,
            MessageOp.OP_NAME
    )
    private lateinit var module: GTXModule
    private lateinit var me: BlockchainRid
    private lateinit var cryptoSystem: CryptoSystem
    private var hasRateLimitQuery: Boolean = false
    private var systemAnchoringBrid: BlockchainRid? = null

    private val blockedPipes = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        this.module = module
        me = blockchainRID
        cryptoSystem = cs
        hasRateLimitQuery = module.getQueries().contains(RECEIVER_RATE_LIMIT_QUERY_NAME)
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
        val allOps = mutableListOf<OpData>()
        val messageLimit = AtomicLong(icmfReceiverBlockchainConfigData.messageLimit)
        createNonAnchoredOperations(
                bctx,
                allOps,
                nonAnchoredReceivers.flatMap { it.getRelevantPipes() },
                BASE_SPECIAL_TX_OVERHEAD,
                messageLimit
        ).let { size ->
            createAnchoredOperations(
                    bctx,
                    allOps,
                    anchoredReceivers.flatMap { it.getRelevantPipes() },
                    size,
                    messageLimit
            )
        }
        bctx.addAfterCommitHook {
            blockedPipes.clear()
        }
        return allOps
    }

    private fun createNonAnchoredOperations(
            bctx: BlockEContext,
            allOps: MutableList<OpData>,
            pipes: List<IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>>,
            initialSize: Int,
            messageLimit: AtomicLong
    ): Int {
        var currentSize = initialSize
        var isFull = false
        for (pipe in pipes) {
            val blockchainRid = pipe.id

            if (pipe.mightHaveNewPackets() && !isFull && pipe.route.topic !in blockedPipes && !delayPipe(bctx, blockchainRid, pipe.route.topic)) {
                var currentMessageHeight: Long =
                        dbOperations.loadLastMessageHeight(bctx, blockchainRid, pipe.route.topic)

                // Clean up packets that are no longer relevant
                pipe.markTaken(currentMessageHeight, bctx)

                while (pipe.mightHaveNewPackets() && !isFull) {
                    val icmfPackets = pipe.fetchNext(currentMessageHeight)
                    if (icmfPackets != null) {
                        for (packet in icmfPackets.packets) {
                            val spilledMessageCounts =
                                    dbOperations.loadSpilledMessageCounts(bctx, "", packet.height, pipe.route.topic)
                            val spilledCount = spilledMessageCounts[packet.sender] ?: 0
                            val (newSize, filled) = processMessages(
                                    spilledCount,
                                    packet,
                                    currentSize,
                                    allOps,
                                    isFull,
                                    currentMessageHeight,
                                    messageLimit,
                                    { header, witness ->
                                        NonAnchoredHeaderOp(header, witness).toOpData()
                                    },
                            )
                            currentSize = newSize
                            isFull = filled
                        }
                        if (!isFull) pipe.markTaken(icmfPackets.currentPointer, bctx)
                        currentMessageHeight = icmfPackets.currentPointer
                    } else {
                        break // Nothing more to find
                    }
                }
            }
        }
        return currentSize
    }

    private fun createAnchoredOperations(
            bctx: BlockEContext,
            allOps: MutableList<OpData>,
            pipes: List<IcmfPipe<TopicRoute, Long, IcmfAnchorPacket, String>>,
            initialSize: Int,
            messageLimit: AtomicLong
    ): Int {
        val lastAnchoredHeights =
                dbOperations.loadLastAnchoredHeights(bctx).associate { (it.cluster to it.topic) to it.height }

        var currentSize = initialSize
        var isFull = false
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets() && !isFull && pipe.route.topic !in blockedPipes) {
                val clusterName = pipe.id

                var currentAnchorHeight: Long = lastAnchoredHeights[clusterName to pipe.route.topic] ?: -1

                // Clean up packets that are no longer relevant
                pipe.markTaken(currentAnchorHeight, bctx)

                while (pipe.mightHaveNewPackets() && !isFull) {
                    val icmfPackets = pipe.fetchNext(currentAnchorHeight)
                    if (icmfPackets != null) {
                        for (anchorPacket in icmfPackets.packets) {
                            if (isFull) break

                            val spilledMessageCounts = dbOperations.loadSpilledMessageCounts(
                                    bctx,
                                    clusterName,
                                    anchorPacket.height,
                                    pipe.route.topic
                            )
                            if (spilledMessageCounts.isEmpty()) {
                                val anchorHeaderOp = AnchorHeaderOp(
                                        clusterName,
                                        anchorPacket.rawAnchorHeader,
                                        anchorPacket.rawAnchorWitness
                                ).toOpData()
                                allOps.add(anchorHeaderOp)
                                currentSize += anchorHeaderOp.getEncodedSize()
                            }

                            for (packet in anchorPacket.packets) {
                                val spilledCount = spilledMessageCounts[packet.sender] ?: 0
                                val currentPrevMessageBlockHeight =
                                        dbOperations.loadLastMessageHeight(bctx, packet.sender, packet.topic)
                                val (newSize, filled) = processMessages(
                                        spilledCount,
                                        packet,
                                        currentSize,
                                        allOps,
                                        isFull,
                                        currentPrevMessageBlockHeight,
                                        messageLimit,
                                        { header, witness ->
                                            AnchoredHeaderOp(header, witness).toOpData()
                                        },
                                )
                                currentSize = newSize
                                isFull = filled
                            }
                            if (!isFull) pipe.markTaken(icmfPackets.currentPointer, bctx)
                            currentAnchorHeight = icmfPackets.currentPointer
                        }
                    } else {
                        break // Nothing more to find
                    }
                }
            }
        }
        return currentSize
    }

    /**
     * Calculates how much space is needed to fit header and message hash ops
     *
     * The only variable in hash op size is topic, so we can use placeholders for brid and message hash
     */
    private fun calculateIcmfPacketOverheadSize(icmfPacket: IcmfPacket, headerOp: OpData) = headerOp.getEncodedSize() +
            MessageHashOp(BlockchainRid.ZERO_RID, icmfPacket.topic, EMPTY_HASH).toOpData()
                    .getEncodedSize() * icmfPacket.messages.size

    private fun processMessages(
            spilledCount: Int,
            packet: IcmfPacket,
            initialSize: Int,
            allOps: MutableList<OpData>,
            isFull: Boolean,
            currentPrevMessageBlockHeight: Long,
            messageLimit: AtomicLong,
            headerOpCreator: (ByteArray, ByteArray) -> OpData,
    ): Pair<Int, Boolean> {
        var currentSize = initialSize
        var filled = isFull
        if (spilledCount > 0) {
            for (message in packet.messages.subList(packet.messages.size - spilledCount, packet.messages.size)) {
                val messageOp = MessageOp(packet.sender, packet.topic, message.body).toOpData()
                val messageOpSize = messageOp.getEncodedSize()
                if (!filled && currentSize + messageOpSize < maxTxSize - specialTxSizeMargin && messageLimit.getAndDecrement() > 0) {
                    allOps.add(messageOp)
                    currentSize += messageOpSize
                } else {
                    filled = true
                    break
                }
            }
        } else if (packet.height > currentPrevMessageBlockHeight) {
            val headerOp = headerOpCreator(packet.rawHeader, packet.rawWitness)
            val overheadSize = calculateIcmfPacketOverheadSize(packet, headerOp)
            if (currentSize + overheadSize < maxTxSize - specialTxSizeMargin && messageLimit.get() > 0) {
                currentSize += overheadSize
                allOps.add(headerOp)

                for (message in packet.messages) {
                    allOps.add(
                            MessageHashOp(
                                    packet.sender,
                                    packet.topic,
                                    message.body.merkleHash(packet.merkleHashCalculator)
                            ).toOpData()
                    )

                    val messageOp = MessageOp(packet.sender, packet.topic, message.body).toOpData()
                    val messageOpSize = messageOp.getEncodedSize()
                    if (!filled && currentSize + messageOpSize < maxTxSize - specialTxSizeMargin && messageLimit.getAndDecrement() > 0) {
                        allOps.add(messageOp)
                        currentSize += messageOpSize
                    } else {
                        filled = true
                    }
                }
            } else {
                filled = true
            }
        }
        // else already processed in the previous block, so skip it here

        return Pair(currentSize, filled)
    }

    private fun delayPipe(bctx: BlockEContext, sender: BlockchainRid, topic: String): Boolean = try {
        if (hasRateLimitQuery) {
            module.query(
                    bctx, RECEIVER_RATE_LIMIT_QUERY_NAME,
                    GtvObjectMapper.toGtvDictionary(
                            ReceiverRateLimitRequest(
                                    sender = sender.data,
                                    topic = topic,
                            )
                    )
            ).asDict()
        }
        false
    } catch (e: UserMistake) {
        logger.debug { "In block ${bctx.height}, messages from $sender in topic $topic are delayed until next block: ${e.message}" }
        true
    }

    /**
     * I am validator, validate messages.
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        var currentAnchorHeaderData: AnchorHeaderValidationInfo? = null
        val headerBlockRidsByTopic: MutableMap<String, MutableList<ByteArray>> = mutableMapOf()
        var currentHeaderData: HeaderValidationInfo? = null
        val bodyHashesByTopic: MutableMap<String, MutableList<ByteArray>> = mutableMapOf()
        val bodyHashesBySenderAndTopic: MutableMap<Pair<BlockchainRid, String>, MutableList<MessageHash>> =
                mutableMapOf()
        val peerCache = mutableMapOf<BlockchainRid, Pair<ByteArray, Collection<PubKey>>>()
        var messageLimit = icmfReceiverBlockchainConfigData.messageLimit
        for (op in ops) {
            when (op.opName) {
                AnchorHeaderOp.OP_NAME -> {
                    val anchorHeaderOp = AnchorHeaderOp.fromOpData(op) ?: return false

                    if (!validateHeaders(
                                    headerBlockRidsByTopic.filter { it.key in bodyHashesByTopic.keys },
                                    currentAnchorHeaderData,
                                    bctx
                            )
                    ) return false
                    if (currentAnchorHeaderData != null) {
                        for (topic in headerBlockRidsByTopic.keys) {
                            if (bodyHashesByTopic.containsKey(topic)) {
                                dbOperations.saveLastAnchoredHeight(
                                        bctx,
                                        currentAnchorHeaderData.cluster,
                                        topic,
                                        currentAnchorHeaderData.height
                                )
                            }
                        }
                    }
                    headerBlockRidsByTopic.clear()

                    val decodedHeader = BlockHeaderData.fromBinary(anchorHeaderOp.rawHeader)
                    val anchorHeaderHashCalculator = makeMerkleHashCalculator(decodedHeader.getMerkleHashVersion())
                    val blockRid = decodedHeader.toGtv().merkleHash(anchorHeaderHashCalculator)
                    val peers = getCachedPeers(peerCache, decodedHeader, blockchainConfigProvider) ?: return false

                    val anchorHeaderData = TopicHeaderData.extractTopicHeaderData(
                            decodedHeader,
                            anchorHeaderOp.rawHeader,
                            anchorHeaderOp.rawWitness,
                            blockRid,
                            cryptoSystem,
                            peers,
                            ICMF_ANCHOR_HEADERS_EXTRA
                    )
                            ?: return false

                    currentAnchorHeaderData = AnchorHeaderValidationInfo(
                            decodedHeader.getHeight(),
                            anchorHeaderOp.cluster,
                            anchorHeaderData,
                            anchorHeaderHashCalculator
                    )
                }

                AnchoredHeaderOp.OP_NAME, NonAnchoredHeaderOp.OP_NAME -> {
                    val headerOp = if (op.opName == AnchoredHeaderOp.OP_NAME) {
                        AnchoredHeaderOp.fromOpData(op) ?: return false
                    } else {
                        NonAnchoredHeaderOp.fromOpData(op) ?: return false
                    }

                    if (!validateMessages(bodyHashesByTopic, currentHeaderData, bctx)) return false
                    bodyHashesByTopic.clear()

                    if (headerOp is NonAnchoredHeaderOp && currentAnchorHeaderData != null) {
                        logger.warn("got ${AnchorHeaderOp.OP_NAME} before a ${NonAnchoredHeaderOp.OP_NAME}")
                        return false
                    }

                    val decodedHeader = BlockHeaderData.fromBinary(headerOp.rawHeader)
                    val messageHeaderHashVersion = decodedHeader.getMerkleHashVersion()
                    val messageHeaderHashCalculator = makeMerkleHashCalculator(messageHeaderHashVersion)
                    val blockRid = decodedHeader.toGtv().merkleHash(messageHeaderHashCalculator)
                    val peers = getCachedPeers(peerCache, decodedHeader, blockchainConfigProvider) ?: return false

                    val topicData = TopicHeaderData.extractTopicHeaderData(
                            decodedHeader,
                            headerOp.rawHeader,
                            headerOp.rawWitness,
                            blockRid,
                            cryptoSystem,
                            peers,
                            ICMF_BLOCK_HEADER_EXTRA
                    )
                            ?: return false

                    if (headerOp is AnchoredHeaderOp) {
                        for (topic in topicData.keys) {
                            headerBlockRidsByTopic.computeIfAbsent(topic) { mutableListOf() }
                                    .add(blockRid)
                        }
                    }
                    currentHeaderData = HeaderValidationInfo(
                            decodedHeader.getHeight(),
                            decodedHeader.getBlockchainRid(),
                            topicData,
                            messageHeaderHashVersion,
                            messageHeaderHashCalculator,
                    )
                }

                MessageHashOp.OP_NAME -> {
                    val messageHashOp = MessageHashOp.fromOpData(op) ?: return false

                    if (currentHeaderData == null) {
                        logger.warn("got ${MessageHashOp.OP_NAME} before any ${AnchoredHeaderOp.OP_NAME} or ${NonAnchoredHeaderOp.OP_NAME}")
                        return false
                    }

                    if (currentAnchorHeaderData != null) {
                        if (!validateMessageSenderAndTopic(messageHashOp.sender, messageHashOp.topic)) return false
                    } else {
                        if (!validateNonAnchoredMessageSenderAndTopic(
                                        messageHashOp.sender,
                                        messageHashOp.topic,
                                        currentHeaderData.height
                                )
                        ) return false
                    }

                    val topicData = currentHeaderData.icmfHeaderData[messageHashOp.topic]
                    if (topicData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${messageHashOp.topic} for sender ${messageHashOp.sender.toHex()}")
                        return false
                    }

                    bodyHashesByTopic.computeIfAbsent(messageHashOp.topic) { mutableListOf() }
                            .add(messageHashOp.hash)

                    bodyHashesBySenderAndTopic.computeIfAbsent(messageHashOp.sender to messageHashOp.topic) { mutableListOf() }
                            .add(MessageHash(messageHashOp.hash, currentHeaderData.merkleHashVersion))
                }

                MessageOp.OP_NAME -> {
                    if (messageLimit-- <= 0 && isSigner()) {
                        logger.warn("Number of messages exceed limit of ${icmfReceiverBlockchainConfigData.messageLimit}")
                        return false
                    }
                    val messageOp = MessageOp.fromOpData(op) ?: return false
                    val latestReceivedHashForTopic =
                            bodyHashesBySenderAndTopic[messageOp.sender to messageOp.topic]?.removeLastOrNull()
                    if (latestReceivedHashForTopic == null) {
                        val spilledMessage =
                                dbOperations.loadOldestSpilledMessage(bctx, messageOp.sender, messageOp.topic)
                        if (spilledMessage == null) {
                            logger.warn("Received unexpected message op for sender ${messageOp.sender} and topic ${messageOp.topic}")
                            return false
                        }

                        val messageBodyHash =
                                messageOp.body.merkleHash(makeMerkleHashCalculator(spilledMessage.merkleHashVersion))
                        if (!spilledMessage.hash.contentEquals(messageBodyHash)) {
                            logger.warn("Hash of message body ${messageBodyHash.toHex()} does not match latest spilled message hash operation value ${spilledMessage.hash.toHex()} for topic ${messageOp.topic}")
                            return false
                        }
                        dbOperations.imprecateSpilledMessage(bctx, spilledMessage.serial)

                        if (dbOperations.loadSpilledMessageCounts(
                                        bctx,
                                        spilledMessage.cluster,
                                        spilledMessage.anchorHeight,
                                        messageOp.topic
                                ).isEmpty()
                        ) {
                            dbOperations.saveLastAnchoredHeight(
                                    bctx,
                                    spilledMessage.cluster,
                                    messageOp.topic,
                                    spilledMessage.anchorHeight
                            )
                        }
                    } else { // no spill
                        if (currentHeaderData == null) {
                            logger.warn("got ${MessageOp.OP_NAME} before any ${AnchoredHeaderOp.OP_NAME} or ${NonAnchoredHeaderOp.OP_NAME} when there was no spill")
                            return false
                        }
                        val messageBodyHash = messageOp.body.merkleHash(currentHeaderData.merkleHashCalculator)
                        if (!latestReceivedHashForTopic.hash.contentEquals(messageBodyHash)) {
                            logger.warn("Hash of message body ${messageBodyHash.toHex()} does not match latest received message hash operation value ${latestReceivedHashForTopic.hash.toHex()} for topic ${messageOp.topic}")
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

        if (!validateMessages(bodyHashesByTopic, currentHeaderData, bctx)) {
            return false
        }
        if (!validateHeaders(
                        headerBlockRidsByTopic.filter { it.key in bodyHashesByTopic.keys },
                        currentAnchorHeaderData,
                        bctx
                )
        ) {
            return false
        }

        if (currentAnchorHeaderData != null) {
            for ((key, hashes) in bodyHashesBySenderAndTopic) {
                val (sender, topic) = key
                hashes.forEach {
                    dbOperations.saveSpilledMessage(
                            bctx,
                            currentAnchorHeaderData.cluster,
                            currentAnchorHeaderData.height,
                            sender,
                            topic,
                            it.hash,
                            it.merkleHashVersion
                    )
                }
            }

            for (topic in headerBlockRidsByTopic.keys) {
                // If we actually received messages on the topic and there is no spill we can save as last anchor height
                if (bodyHashesByTopic.containsKey(topic) && bodyHashesBySenderAndTopic.filterKeys { it.second == topic }.values.all { it.isEmpty() }) {
                    dbOperations.saveLastAnchoredHeight(
                            bctx,
                            currentAnchorHeaderData.cluster,
                            topic,
                            currentAnchorHeaderData.height
                    )
                }
            }
        } else if (currentHeaderData != null) {
            for ((key, hashes) in bodyHashesBySenderAndTopic) {
                val (sender, topic) = key
                hashes.forEach {
                    dbOperations.saveSpilledMessage(
                            bctx,
                            "",
                            currentHeaderData.height,
                            sender,
                            topic,
                            it.hash,
                            it.merkleHashVersion
                    )
                }
            }
        }

        return true
    }

    private fun validateNonAnchoredMessageSenderAndTopic(sender: BlockchainRid, topic: String, height: Long): Boolean {
        if (icmfReceiverBlockchainConfigData.local?.any { BlockchainRid(it.blockchainRid) == sender && it.topic == topic && height >= it.skipToHeight } == true
                || icmfReceiverBlockchainConfigData.localToMe?.any { BlockchainRid(it.blockchainRid) == sender && topic == topicWithReceiver(it.topic, me) } == true
                || (icmfReceiverBlockchainConfigData.anchoring?.topics?.contains(topic) == true && isAnchoringChain(sender))
                || (icmfReceiverBlockchainConfigData.anchoringToMe?.topics?.any { topic == topicWithReceiver(it, me) } == true && isAnchoringChain(sender))
                || (icmfReceiverBlockchainConfigData.directoryChain?.topics?.contains(topic) == true && sender == directoryChainBrid)
                || (icmfReceiverBlockchainConfigData.directoryChainToMe?.topics?.any { topic == topicWithReceiver(it, me) } == true && sender == directoryChainBrid)
        ) {
            return true
        }

        logger.warn("Blockchain $sender is not allowed to send us local non-anchored messages on topic $topic at height $height")
        return false
    }

    private fun validateMessageSenderAndTopic(sender: BlockchainRid, topic: String): Boolean {
        if (icmfReceiverBlockchainConfigData.global?.topics?.contains(topic) == true
                || icmfReceiverBlockchainConfigData.global?.blockchains?.any { BlockchainRid(it.blockchainRid) == sender && it.topic == topic } == true
                || icmfReceiverBlockchainConfigData.local?.any { BlockchainRid(it.blockchainRid) == sender && it.topic == topic } == true
                || icmfReceiverBlockchainConfigData.localToMe?.any { BlockchainRid(it.blockchainRid) == sender && topic == topicWithReceiver(it.topic, me) } == true
                || (icmfReceiverBlockchainConfigData.anchoring?.topics?.contains(topic) == true && isAnchoringChain(sender))
                || (icmfReceiverBlockchainConfigData.anchoringToMe?.topics?.any { topic == topicWithReceiver(it, me) } == true && isAnchoringChain(sender))
                || (icmfReceiverBlockchainConfigData.directoryChain?.topics?.contains(topic) == true && sender == directoryChainBrid)
                || (icmfReceiverBlockchainConfigData.directoryChainToMe?.topics?.any { topic == topicWithReceiver(it, me) } == true && sender == directoryChainBrid)
        ) {
            return true
        }

        logger.warn("Blockchain $sender is not allowed to send us messages on topic $topic")
        return false
    }

    private fun isAnchoringChain(sender: BlockchainRid): Boolean {
        if (systemAnchoringBrid == null) {
            systemAnchoringBrid = clusterManagement.getSystemAnchoringChain()
        }

        return sender == systemAnchoringBrid || clusterManagement.getClusterAnchoringChains().contains(sender)
    }

    private fun validateHeaders(
            headerBlockRids: Map<String, MutableList<ByteArray>>,
            currentAnchorHeaderData: AnchorHeaderValidationInfo?,
            bctx: BlockEContext
    ): Boolean {
        if (currentAnchorHeaderData != null && headerBlockRids.isNotEmpty()) {
            for ((topic, blockRids) in headerBlockRids) {
                val hash = gtv(blockRids.map { gtv(it) }).merkleHash(currentAnchorHeaderData.merkleHashCalculator)

                val anchorHeaderData = currentAnchorHeaderData.anchorHeaderData[topic]
                if (anchorHeaderData == null) {
                    logger.warn("$ICMF_ANCHOR_HEADERS_EXTRA missing data for topic $topic")
                    return false
                }

                if (!validatePreviousHeaderHeight(
                                bctx,
                                currentAnchorHeaderData.cluster,
                                topic,
                                anchorHeaderData.previousBlockHeight
                        )
                ) return false

                if (!hash.contentEquals(anchorHeaderData.hash)) {
                    logger.warn("Invalid block-rid hash, expected ${anchorHeaderData.hash.toHex()} but was ${hash.toHex()}")
                    return false
                }
            }
        } else if (headerBlockRids.isNotEmpty()) {
            logger.warn("got ${AnchoredHeaderOp.OP_NAME} before any ${AnchorHeaderOp.OP_NAME}")
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
            for (topic in bodyHashesByTopic.keys) {
                val topicData = currentHeaderData.icmfHeaderData[topic]
                // We should already have validated this when checking hashes, but it does not hurt to do it again
                if (topicData == null) {
                    logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic")
                    return false
                }

                if (!validatePrevMessageHeight(
                                bctx,
                                currentHeaderData.sender,
                                topic,
                                topicData.previousBlockHeight,
                                currentHeaderData.height
                        )
                ) return false
            }
        } else if (bodyHashesByTopic.isNotEmpty()) {
            logger.warn("got ${MessageOp.OP_NAME} before any ${AnchoredHeaderOp.OP_NAME}")
            return false
        }
        return true
    }

    private fun validatePreviousHeaderHeight(
            bctx: BlockEContext,
            cluster: String,
            topic: String,
            previousHeight: Long
    ): Boolean {
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
        val skipToHeight =
                icmfReceiverBlockchainConfigData.local?.find { it.blockchainRid.contentEquals(sender) && it.topic == topic }
                        ?.skipToHeight ?: 0
        val currentPrevMessageBlockHeight = dbOperations.loadLastMessageHeight(bctx, BlockchainRid(sender), topic)

        if ((skipToHeight == 0L || prevMessageBlockHeight >= skipToHeight) && prevMessageBlockHeight != currentPrevMessageBlockHeight) {
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
        if (bodyHashesByTopic.isEmpty()) {
            logger.warn("Received header ops but no message ops")
            return false
        }
        for ((topic, hashes) in bodyHashesByTopic) {
            val topicData = headerData.icmfHeaderData[topic]
            if (topicData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic")
                return false
            }

            val computedHash = gtv(hashes.map { gtv(it) }).merkleHash(headerData.merkleHashCalculator)
            if (!topicData.hash.contentEquals(computedHash)) {
                logger.warn("invalid messages hash for topic: $topic")
                return false
            }
        }
        return true
    }

    fun blockPipe(topic: String) {
        blockedPipes += topic
    }

    private var shouldBuildBlockCheckTime = 0L
    private var shouldBuildBlockNow = false

    override fun blockCommitted(blockData: BlockData) {
        shouldBuildBlockCheckTime = clock.millis()
        shouldBuildBlockNow = false
    }

    override fun shouldBuildBlock(): Boolean {
        if (!::icmfReceiverBlockchainConfigData.isInitialized) return false

        if (shouldBuildBlockNow) return true

        val now = clock.millis()
        if (now - shouldBuildBlockCheckTime < icmfReceiverBlockchainConfigData.messageCheckInterval) return false
        shouldBuildBlockCheckTime = clock.millis()

        val hasMessages = (nonAnchoredReceivers.flatMap { it.getRelevantPipes() } + anchoredReceivers.flatMap { it.getRelevantPipes() })
                .filterNot { it.route.topic in blockedPipes }
                .any { it.haveNewPacketsForSure() }
        if (hasMessages) {
            shouldBuildBlockNow = true
            return true
        } else {
            return false
        }
    }

    data class AnchorHeaderValidationInfo(
            val height: Long,
            val cluster: String,
            val anchorHeaderData: Map<String, TopicHeaderData>,
            val merkleHashCalculator: GtvMerkleHashCalculatorBase
    )

    data class HeaderValidationInfo(
            val height: Long,
            val sender: ByteArray,
            val icmfHeaderData: Map<String, TopicHeaderData>,
            val merkleHashVersion: Long,
            val merkleHashCalculator: GtvMerkleHashCalculatorBase
    )

    data class AnchorHeaderOp(
            val cluster: String,
            val rawHeader: ByteArray,
            val rawWitness: ByteArray
    ) {
        companion object {
            // operation __icmf_anchor_header(cluster: text, block_header: byte_array, witness: byte_array)
            const val OP_NAME = "__icmf_anchor_header"

            val metadata = OperationMetadata(
                    args = listOf(
                            ArgumentMetadata(name = "cluster", gtvTypes = setOf(GtvType.STRING)),
                            ArgumentMetadata(name = "block_header", gtvTypes = setOf(GtvType.BYTEARRAY)),
                            ArgumentMetadata(name = "witness", gtvTypes = setOf(GtvType.BYTEARRAY)),
                    )
            )

            fun fromOpData(opData: OpData): AnchorHeaderOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    AnchorHeaderOp(
                            opData.args[0].asString(),
                            opData.args[1].asByteArray(),
                            opData.args[2].asByteArray()
                    )
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(cluster), gtv(rawHeader), gtv(rawWitness)))
    }

    interface HeaderOp {
        val rawHeader: ByteArray
        val rawWitness: ByteArray
    }

    data class AnchoredHeaderOp(
            override val rawHeader: ByteArray,
            override val rawWitness: ByteArray
    ) : HeaderOp {
        companion object {
            // operation __anchored_icmf_header(block_header: byte_array, witness: byte_array)
            const val OP_NAME = "__anchored_icmf_header"

            val metadata = OperationMetadata(
                    args = listOf(
                            ArgumentMetadata(name = "block_header", gtvTypes = setOf(GtvType.BYTEARRAY)),
                            ArgumentMetadata(name = "witness", gtvTypes = setOf(GtvType.BYTEARRAY)),
                    )
            )

            fun fromOpData(opData: OpData): AnchoredHeaderOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 2) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    AnchoredHeaderOp(opData.args[0].asByteArray(), opData.args[1].asByteArray())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(rawHeader), gtv(rawWitness)))
    }

    data class NonAnchoredHeaderOp(
            override val rawHeader: ByteArray,
            override val rawWitness: ByteArray
    ) : HeaderOp {
        companion object {
            // operation __non_anchored_icmf_header(block_header: byte_array, witness: byte_array)
            const val OP_NAME = "__non_anchored_icmf_header"

            val metadata = OperationMetadata(
                    args = listOf(
                            ArgumentMetadata(name = "block_header", gtvTypes = setOf(GtvType.BYTEARRAY)),
                            ArgumentMetadata(name = "witness", gtvTypes = setOf(GtvType.BYTEARRAY)),
                    )
            )

            fun fromOpData(opData: OpData): NonAnchoredHeaderOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 2) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    NonAnchoredHeaderOp(opData.args[0].asByteArray(), opData.args[1].asByteArray())
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
            // operation __anchored_icmf_message_hash(sender: byte_array, topic: text, hash: byte_array)
            const val OP_NAME = "__anchored_icmf_message_hash"

            val metadata = OperationMetadata(
                    args = listOf(
                            ArgumentMetadata(name = "sender", gtvTypes = setOf(GtvType.BYTEARRAY)),
                            ArgumentMetadata(name = "topic", gtvTypes = setOf(GtvType.STRING)),
                            ArgumentMetadata(name = "hash", gtvTypes = setOf(GtvType.BYTEARRAY)),
                    )
            )

            fun fromOpData(opData: OpData): MessageHashOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    MessageHashOp(
                            BlockchainRid(opData.args[0].asByteArray()),
                            opData.args[1].asString(),
                            opData.args[2].asByteArray()
                    )
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

            val metadata = OperationMetadata(
                    args = listOf(
                            ArgumentMetadata(name = "sender", gtvTypes = setOf(GtvType.BYTEARRAY)),
                            ArgumentMetadata(name = "topic", gtvTypes = setOf(GtvType.STRING)),
                            ArgumentMetadata(
                                    name = "body", gtvTypes = setOf(
                                    GtvType.NULL,
                                    GtvType.BYTEARRAY,
                                    GtvType.STRING,
                                    GtvType.INTEGER,
                                    GtvType.DICT,
                                    GtvType.ARRAY,
                                    GtvType.BIGINTEGER
                            )
                            ),
                    )
            )

            fun fromOpData(opData: OpData): MessageOp? = fromOpNameAndArgs(opData.opName, opData.args)

            fun fromOpNameAndArgs(opName: String, args: Array<out Gtv>): MessageOp? {
                if (opName != OP_NAME) return null
                if (args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${args.size}")
                    return null
                }

                return try {
                    MessageOp(BlockchainRid(args[0].asByteArray()), args[1].asString(), args[2])
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(sender), gtv(topic), body))
    }
}

fun OpData.getEncodedSize() = GtvEncoder.encodeGtv(gtv(gtv(opName), gtv(args.toList()))).size

data class MessageHash(
        val hash: ByteArray,
        val merkleHashVersion: Long
)
