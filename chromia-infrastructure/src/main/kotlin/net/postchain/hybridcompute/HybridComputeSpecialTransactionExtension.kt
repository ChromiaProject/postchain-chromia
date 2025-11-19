package net.postchain.hybridcompute

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.containers.ContainerRateLimit
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.core.block.BlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXBlockBuildingAffectingSpecialTxExtension
import net.postchain.hybridcompute.rell.lib.hybridcompute.ComputeRequest
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUEST
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.IS_REQUEST_FAILED
import net.postchain.hybridcompute.rell.lib.hybridcompute.TakenComputeRequest
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class HybridComputeSpecialTransactionExtension(private val dbOperations: HybridComputeDatabaseOperations)
    : GTXBlockBuildingAffectingSpecialTxExtension, Shutdownable {
    companion object : KLogging() {
        val DEFAULT_PERIOD_LENGTH = 7.days // 1 week
    }

    internal var container: String? = null
    internal var containerCreationTime: Instant? = null
    internal var containerRateLimits: Map<String, ContainerRateLimit> = mapOf()
    internal lateinit var config: HybridComputeConfig
    internal lateinit var engine: HybridComputeEngine

    private lateinit var module: GTXModule
    private lateinit var loader: Thread
    private val loaded = AtomicBoolean(false)
    var hasDistributedTimeout: Boolean = false

    private lateinit var nodePubkey: ByteArray
    private lateinit var sigMaker: SigMaker
    private lateinit var cs: CryptoSystem
    var chainID: Long = -1
    private lateinit var blockchainRID: BlockchainRid

    override fun getRelevantOps(): Set<String> = setOf(RequestTakenOp.OP_NAME, ResponseOp.OP_NAME, FailureOp.OP_NAME, ClusterTimeoutOp.OP_NAME)

    private val computations = ConcurrentHashMap<String, Computation>() // id -> computation

    private val computer: ExecutorService by lazy {
        ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(config.concurrency.toInt()),
                ThreadFactoryBuilder().setNameFormat("hybridcompute-compute-%d").build()
        )
    }
    private val timeouter: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor(
                ThreadFactoryBuilder().setNameFormat("hybridcompute-timeout-%d").setDaemon(true).build()
        )
    }

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        this.module = module
        this.cs = cs
        this.chainID = chainID
        this.blockchainRID = blockchainRID
    }

    fun initSigMaker(pubKeyByteArray: ByteArray, privKeyByteArray: ByteArray) {
        this.nodePubkey = pubKeyByteArray
        this.sigMaker = cs.buildSigMaker(KeyPair(nodePubkey, privKeyByteArray))
    }

    fun load() {
        val timeoutFuture = AtomicReference<ScheduledFuture<*>?>()
        loader = thread(name = "hybridcompute-load") {
            withLoggingContext(mapOf(
                    CHAIN_IID_TAG to chainID.toString(),
                    BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
            )) {
                logger.info("Loading engine...")
                try {
                    val duration = measureTime {
                        engine.load()
                    }
                    logger.info("Engine loaded in $duration")
                    loaded.set(true)
                } catch (e: UserMistake) {
                    logger.warn("Loading engine failed: ${e.message}")
                } catch (_: InterruptedException) {
                    logger.debug { "Loading engine interrupted" }
                } catch (e: Exception) {
                    logger.warn("Loading engine failed unexpectedly: $e", e)
                } finally {
                    timeoutFuture.get()?.cancel(false)
                }
            }
        }
        if (config.loadTimeoutSeconds > 0) {
            timeoutFuture.set(timeouter.schedule({
                withLoggingContext(mapOf(
                        CHAIN_IID_TAG to chainID.toString(),
                        BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                )) {
                    logger.warn("Loading timed out after ${config.loadTimeoutSeconds} seconds, interrupting it")
                    loader.interrupt()
                }
            }, config.loadTimeoutSeconds, TimeUnit.SECONDS))
        }
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean =
            position == SpecialTransactionPosition.Begin || position == SpecialTransactionPosition.End

    override fun blockCommitted(blockData: BlockData) {}

    override fun shouldBuildBlock(): Boolean = computations.any { it.value is FinishedComputation || it.value is FailedComputation }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        if (!loaded.get()) {
            logger.info("Engine not loaded yet, returning empty list from createSpecialOperations")
            return listOf()
        }
        val now = Instant.now()
        when (position) {
            SpecialTransactionPosition.Begin ->
                return buildList {
                    if (hasDistributedTimeout) {
                        computations.keys.removeIf { id -> module.query(bctx, IS_REQUEST_FAILED, gtv(Pair("id", gtv(id)))).asBoolean() }
                    }
                    for ((id, computation) in computations) {
                        when (computation) {
                            is TakenComputation -> {
                                logger.warn("Request id [$id] of type [${computation.type}] was taken but not started, removing")
                                computations.remove(id)
                            }

                            is StartedComputation -> {} // nothing to do

                            is FinishedComputation -> {
                                logger.info("Submitting successful response for request id [$id] of type [${computation.type}]")
                                val signature = sigMaker.signDigest(hash(id, blockchainRID.toHex(), bctx.height))
                                add(ResponseOp(id, computation.type, computation.input, computation.output, signature.subjectID, signature.data).toOpData())
                                bctx.addAfterCommitHook { computations.remove(id) }
                            }

                            is FailedComputation -> {
                                logger.info("Submitting failed response for request id [$id] of type [${computation.type}]")
                                val signature = sigMaker.signDigest(hash(id, blockchainRID.toHex(), bctx.height))
                                add(FailureOp(id, computation.type, computation.input, computation.errorMessage, signature.subjectID, signature.data).toOpData())
                                bctx.addAfterCommitHook { computations.remove(id) }
                            }
                        }
                    }

                    if (hasDistributedTimeout) {
                        for (request in module.query(bctx, GET_TAKEN_REQUESTS, gtv(mapOf())).asArray().map { it.toObject<TakenComputeRequest>() }) {
                            val takenTimestamp = request.takenTimestamp
                            if (isComputeClusterTimeout(takenTimestamp, now.toEpochMilli())) {
                                logger.warn("Computation of request id [${request.id}] of type [${request.type}] not reported by back by computing node after ${config.computeClusterTimeoutSeconds} seconds")
                                add(ClusterTimeoutOp(request.id, request.type, request.input).toOpData())
                            }
                        }
                    }
                }

            SpecialTransactionPosition.End ->
                return buildList {
                    var takenRequests = 0
                    for (request in module.query(bctx, GET_REQUESTS, gtv(mapOf())).asArray().map { it.toObject<ComputeRequest>() }) {
                        if (engine.name != request.type) {
                            logger.warn("No engine found for request id [${request.id}] of type [${request.type}]")
                            continue
                        }

                        val periodLength = containerRateLimits[request.type]?.periodLength ?: DEFAULT_PERIOD_LENGTH
                        val rateLimit = containerRateLimits[request.type]?.rateLimit ?: Long.MAX_VALUE
                        if (container != null) {
                            val currentPoints = dbOperations.fetchPoints(
                                    bctx, container = container!!, type = request.type, now = now, periodLength = periodLength)
                            val estimatedPoints = try {
                                engine.estimatePoints(request.input)
                            } catch (e: UserMistake) {
                                logger.warn("Estimation of request id [${request.id}] of type [${request.type}] failed, skipping it: ${e.message}")
                                continue
                            } catch (e: Exception) {
                                logger.warn("Estimation of request id [${request.id}] of type [${request.type}] failed unexpectedly, skipping it: $e", e)
                                continue
                            }
                            if (currentPoints + estimatedPoints > rateLimit) {
                                logger.warn("Rate limit for container [$container] and type [${request.type}] exceeded, skipping request id [${request.id}]")
                                continue
                            }
                        }

                        if (takenRequests >= config.concurrency) continue

                        if (computations.putIfAbsent(request.id, TakenComputation(request.type, request.input)) != null) continue

                        val signature = sigMaker.signDigest(hash(request.id, blockchainRID.toHex(), bctx.height))
                        add(RequestTakenOp(request.id, request.type, nodePubkey, signature.data).toOpData())
                        takenRequests++

                        bctx.addAfterCommitHook {
                            try {
                                val timeoutFuture = AtomicReference<ScheduledFuture<*>?>()
                                val future = computer.submit {
                                    withLoggingContext(mapOf(
                                            CHAIN_IID_TAG to chainID.toString(),
                                            BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                                    )) {
                                        computations.replace(request.id, StartedComputation(request.type, request.input))
                                        logger.info("Starting computation of request id [${request.id}] of type [${request.type}]...")
                                        try {
                                            val (outputPointsConsumed, duration) = measureTimedValue { engine.compute(request.input) }
                                            val (output, pointsConsumed) = outputPointsConsumed
                                            if (!Thread.currentThread().isInterrupted) {
                                                logger.info("Computation of request id [${request.id}] of type [${request.type}] finished in $duration")
                                                computations.replace(request.id, FinishedComputation(request.type, request.input, output))
                                            } else {
                                                logger.debug { "Computation of request id [${request.id}] of type [${request.type}] interrupted" }
                                                computations.replace(request.id, FailedComputation(request.type, request.input,"Computation timed out after ${config.computeTimeoutSeconds} seconds"))
                                            }
                                            container?.let {
                                                dbOperations.incrementPoints(bctx, container = it, type = request.type,
                                                        containerCreationTime = containerCreationTime, now = now,
                                                        periodLength = periodLength,
                                                        pointsConsumed = pointsConsumed)
                                            }
                                        } catch (_: InterruptedException) {
                                            logger.debug { "Computation of request id [${request.id}] of type [${request.type}] interrupted with exception" }
                                            computations.replace(request.id, FailedComputation(request.type, request.input, "Computation timed out after ${config.computeTimeoutSeconds} seconds"))
                                        } catch (e: UserMistake) {
                                            logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed: ${e.message}")
                                            computations.replace(request.id, FailedComputation(request.type, request.input,e.message
                                                    ?: "Unknown error"))
                                        } catch (e: Exception) {
                                            logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed unexpectedly: $e", e)
                                            computations.replace(request.id, FailedComputation(request.type, request.input,"Unknown error"))
                                        } finally {
                                            timeoutFuture.get()?.cancel(false)
                                        }
                                    }
                                }
                                if (config.computeTimeoutSeconds > 0) {
                                    timeoutFuture.set(timeouter.schedule({
                                        withLoggingContext(mapOf(
                                                CHAIN_IID_TAG to chainID.toString(),
                                                BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                                        )) {
                                            logger.warn("Computation of request id [${request.id}] of type [${request.type}] timed out after ${config.computeTimeoutSeconds} seconds")
                                            computations.replace(request.id, FailedComputation(request.type, request.input,"Computation timed out after ${config.computeTimeoutSeconds} seconds"))
                                            future.cancel(true) // interrupt the compute thread
                                        }
                                    }, config.computeTimeoutSeconds, TimeUnit.SECONDS))
                                }
                            } catch (_: RejectedExecutionException) {
                                computations.remove(request.id)
                            }
                        }
                    }
                }
        }
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        val now = Instant.now()
        for (op in ops) {
            when (op.opName) {
                RequestTakenOp.OP_NAME -> {
                    val request = RequestTakenOp.fromOpData(op) ?: return false
                    if (!cs.verifyDigest(hash(request.id, blockchainRID.toHex(), bctx.height), Signature(request.processedBy, request.signatureData))) {
                        logger.warn { "Validate request taken operation failed for request id [${request.id}]. Invalid signature." }
                        return false
                    }
                }

                ResponseOp.OP_NAME -> {
                    val response = ResponseOp.fromOpData(op) ?: return false
                    if (engine.name == response.type) {
                        if (!loaded.get()) {
                            logger.warn("Engine not loaded yet, returning false from validateSpecialOperations")
                            return false
                        }
                        if (!computations.containsKey(response.id)) {
                            try {
                                logger.info("Starting validation for request id [${response.id}] of type [${response.type}]...")
                                // TODO POS-1735 have timeout for the validation
                                val duration = measureTime {
                                    engine.validate(response.input, response.output)
                                }
                                logger.info("Validation for request id [${response.id}] of type [${response.type}] succeeded in $duration")
                            } catch (e: UserMistake) {
                                logger.warn("Validation for request id [${response.id}] of type [${response.type}] failed: ${e.message}")
                                return false
                            } catch (e: Exception) {
                                logger.warn("Validation for request id [${response.id}] of type [${response.type}] failed unexpectedly: $e", e)
                                return false
                            }
                        } else {
                            logger.debug { "Skipping validation for request id [${response.id}] of type [${response.type}] on block builder node" }
                        }
                        if (isSignatureInvalid(op.opName, bctx, response.id, response.signatureData)) {
                            return false
                        }
                    } else {
                        logger.warn("No engine found for request id [${response.id}] of type [${response.type}]")
                        return false
                    }
                }

                FailureOp.OP_NAME -> {
                    val failure = FailureOp.fromOpData(op) ?: return false
                    if (engine.name == failure.type) {
                        if (isSignatureInvalid(op.opName, bctx, failure.id, failure.signatureData)) {
                            return false
                        }
                    } else {
                        logger.warn("No engine found for request id [${failure.id}] of type [${failure.type}]")
                        return false
                    }
                }

                ClusterTimeoutOp.OP_NAME -> {
                    val clusterTimeoutOp = ClusterTimeoutOp.fromOpData(op) ?: return false

                    if (engine.name == clusterTimeoutOp.type) {
                        val request = getTakenRequestById(bctx, clusterTimeoutOp.id)
                        if (request.isNull() || !isComputeClusterTimeout(request.toObject<TakenComputeRequest>().takenTimestamp, now.toEpochMilli())) {
                            return false
                        }
                    } else {
                        logger.warn("No engine found for request id [${clusterTimeoutOp.id}] of type [${clusterTimeoutOp.type}]")
                        return false
                    }
                }

                else -> {
                    logger.warn("Unexpected operation: ${op.opName}")
                    return false
                }
            }
        }
        return true
    }

    override fun shutdown() {
        timeouter.shutdownNow()
        computer.shutdown()
        if (!loaded.get() && loader.isAlive) {
            logger.warn("Loading not finished yet, interrupting it")
            loader.interrupt()
            loader.join(1000)
        }
        if (engine is Shutdownable) {
            logger.info("Shutting down engine...")
            val duration = measureTime {
                (engine as Shutdownable).shutdown()
            }
            logger.info("Engine shutdown in $duration")
        }
        logger.info("Shutting down executors")
        computer.shutdownNow()
        if (!timeouter.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Timeouter did not terminate in time")
        }
        if (!computer.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Computer did not terminate in time")
        }
        computations.filterValues { it is StartedComputation }.let {
            if (it.isNotEmpty()) {
                logger.warn("${it.size} computations was not finished: ${it.keys.joinToString(", ")}")
            }
        }
        computations.filterValues { it is FinishedComputation || it is FailedComputation }.let {
            if (it.isNotEmpty()) {
                logger.warn("${it.size} finished computations was not reported: ${it.keys.joinToString(", ")}")
            }
        }
        logger.info("Shut down complete")
    }

    fun isComputeClusterTimeout(takenTimestamp: Long, now: Long) =
            takenTimestamp + config.computeClusterTimeoutSeconds * 1000 < now

    private fun isSignatureInvalid(opName: String, bctx: BlockEContext, id: String, signatureData: ByteArray): Boolean {
        val request = getTakenRequestById(bctx, id)
        if (!request.isNull()) {
            val takenComputeRequest = request.toObject<TakenComputeRequest>()
            if (!cs.verifyDigest(hash(id, blockchainRID.toHex(), bctx.height), Signature(takenComputeRequest.processedBy.data, signatureData))) {
                logger.warn { "Validate $opName operation failed for request id [${takenComputeRequest.id}] of type [${takenComputeRequest.type}]. Invalid signature." }
                return true
            }
        } else {
            logger.warn { "Validate $opName operation failed. Taken request not found by id [${id}]." }
            return true
        }
        return false
    }

    private fun hash(id: String, blockchainRID: String, height: Long) = gtv(gtv(id), gtv(blockchainRID), gtv(height)).merkleHash(GtvMerkleHashCalculatorV2(cs))

    private fun getTakenRequestById(bctx: BlockEContext, id: String) = module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv(id))))
}
