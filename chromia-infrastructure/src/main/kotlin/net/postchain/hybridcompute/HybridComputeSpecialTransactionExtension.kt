package net.postchain.hybridcompute

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.containers.ContainerRateLimit
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.Shutdownable
import net.postchain.core.Storage
import net.postchain.core.block.BlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXBlockBuildingAffectingSpecialTxExtension
import net.postchain.hybridcompute.rell.lib.hybridcompute.ComputeRequest
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_REQUEST
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUEST
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.State
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.time.Clock
import java.time.Instant
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.TimedValue
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class HybridComputeSpecialTransactionExtension(
        private val dbOperations: HybridComputeDatabaseOperations,
        private val clock: Clock = Clock.systemUTC()
) : GTXBlockBuildingAffectingSpecialTxExtension, Shutdownable {
    companion object : KLogging() {
        val DEFAULT_PERIOD_LENGTH = 7.days // 1 week
    }

    internal var container: String? = null
    internal var containerCreationTime: Instant? = null
    internal var containerRateLimits: Map<String, ContainerRateLimit> = mapOf()

    internal var concurrency: Int = -1
    internal var loadTimeoutSeconds: Long = -1
    internal var computeTimeoutSeconds: Long = -1
    internal var computeClusterTimeoutSeconds: Long = -1
    internal var blockBuildingIntervalMillis: Long = -1
    private lateinit var engines: Map<String, Pair<HybridComputeEngine, ExecutorService?>>

    internal lateinit var sharedStorage: Storage
    private lateinit var module: GTXModule
    internal val loaded = AtomicInteger(0)

    private lateinit var nodePubkey: ByteArray
    private lateinit var sigMaker: SigMaker
    private lateinit var cs: CryptoSystem
    private var chainID: Long = -1
    private lateinit var blockchainRID: BlockchainRid

    override fun getRelevantOps(): Set<String> = setOf(RequestTakenOp.OP_NAME, ResponseOp.OP_NAME, FailureOp.OP_NAME, ClusterTimeoutOp.OP_NAME)

    internal val myComputations = ConcurrentHashMap<String, Computation>() // id -> computation
    internal val otherNodesPendingComputations = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val loader: ExecutorService by lazy {
        Executors.newFixedThreadPool(engines.size,
                ThreadFactoryBuilder().setNameFormat("hybridcompute-load-%d").build())
    }
    private val validator: ExecutorService by lazy {
        val size = (engines.values.filterNot { it.first is DatabaseAwareHybridComputeEngine }).size
        Executors.newFixedThreadPool(max(size, 1), // cannot create a thread pool with size 0
                ThreadFactoryBuilder().setNameFormat("hybridcompute-validate-%d").build())
    }
    private val timeouter: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor(
                ThreadFactoryBuilder().setNameFormat("hybridcompute-timeout-%d").setDaemon(true).build())
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

    internal fun setEngines(engineList: List<HybridComputeEngine>, fastEngineList: List<HybridComputeEngine>) {
        engines = engineList.associate { engine ->
            engine.name to (engine to Executors.newSingleThreadExecutor(
                    ThreadFactoryBuilder().setNameFormat("hybridcompute-compute-${engine.name}-%d").build(),
            ))
        } + fastEngineList.associate { engine -> engine.name to (engine to null) }
    }

    fun load() {
        for ((engine, _) in engines.values) {
            loader.submit {
                withLoggingContext(mapOf(
                        CHAIN_IID_TAG to chainID.toString(),
                        BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                )) {
                    var failed = false
                    logger.info("Loading engine ${engine.name}...")
                    try {
                        val duration = measureTime {
                            if (engine is DatabaseAwareHybridComputeEngine) {
                                withReadConnection(sharedStorage, chainID) { ctx: EContext ->
                                    engine.load(ctx)
                                }
                            } else {
                                engine.load()
                            }
                        }
                        if (!Thread.currentThread().isInterrupted) {
                            logger.info("Engine ${engine.name} loaded in $duration")
                        } else {
                            logger.debug { "Loading engine ${engine.name} interrupted" }
                            failed = true
                        }
                    } catch (e: UserMistake) {
                        logger.warn("Loading engine ${engine.name} failed: ${e.message}")
                        failed = true
                    } catch (_: InterruptedException) {
                        logger.debug { "Loading engine ${engine.name} interrupted with exception" }
                        failed = true
                    } catch (e: Exception) {
                        logger.warn("Loading engine ${engine.name} failed unexpectedly: $e", e)
                        failed = true
                    }
                    if (!failed) loaded.incrementAndGet()
                }
            }
        }
        loader.shutdown()
        if (loadTimeoutSeconds > 0) {
            timeouter.schedule({
                withLoggingContext(mapOf(
                        CHAIN_IID_TAG to chainID.toString(),
                        BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                )) {
                    if (!loader.isTerminated) {
                        logger.warn("Loading timed out after $loadTimeoutSeconds seconds, interrupting it")
                        loader.shutdownNow()
                    }
                }
            }, loadTimeoutSeconds, TimeUnit.SECONDS)
        }
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean =
            position == SpecialTransactionPosition.Begin || position == SpecialTransactionPosition.End

    @Volatile
    private var shouldBuildBlockCheckTime = 0L

    @Volatile
    private var shouldBuildBlockNow = false

    override fun blockCommitted(blockData: BlockData) {
        shouldBuildBlockCheckTime = clock.millis()
        shouldBuildBlockNow = false
    }

    override fun shouldBuildBlock(): Boolean {
        if (!isFullyLoaded()) return false

        if (myComputations.any { it.value is FinishedComputation || it.value is FailedComputation }) return true

        if (shouldBuildBlockNow) return true

        val now = clock.millis()
        if (now - shouldBuildBlockCheckTime < blockBuildingIntervalMillis) return false
        shouldBuildBlockCheckTime = clock.millis()

        if (otherNodesPendingComputations.isNotEmpty()) {
            shouldBuildBlockNow = true
            return true
        } else {
            return false
        }
    }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        if (bctx.timestamp <= 0) return listOf() // wait until we have a block timestamp
        if (!isFullyLoaded()) {
            logger.info("Engine(s) not loaded yet, returning empty list from createSpecialOperations")
            return listOf()
        }
        val now = clock.instant()
        when (position) {
            SpecialTransactionPosition.Begin -> {
                if (GET_REQUEST in module.getQueries()) {
                    myComputations.keys.removeIf { id -> getRequestById(bctx, id)?.state in setOf(State.COMPUTED, State.FAILED) }
                    if (computeClusterTimeoutSeconds > 0) {
                        myComputations.keys.removeIf { id ->
                            val request = getRequestById(bctx, id)
                            if (request != null) {
                                if (isComputeClusterTimeout(request.takenTimestamp, bctx.timestamp)) {
                                    logger.warn("Request id [${request.id}] of type [${request.type}] stale after $computeClusterTimeoutSeconds seconds, removing")
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                    }
                }
                return buildList {
                    for ((id, computation) in myComputations) {
                        when (computation) {
                            is TakenComputation -> {
                                logger.warn("Request id [$id] of type [${computation.type}] was taken but not started, removing")
                                myComputations.remove(id)
                            }

                            is StartedComputation -> {} // nothing to do

                            is FinishedComputation -> {
                                if (computation.isFast) {
                                    logger.warn("Request id [$id] of type [${computation.type}] was finished but not committed, removing")
                                    myComputations.remove(id)
                                } else {
                                    logger.info("Submitting successful response for request id [$id] of type [${computation.type}]")
                                    val signature = sigMaker.signDigest(hash(id, blockchainRID.toHex(), bctx.height))
                                    add(ResponseOp(id, computation.type, computation.input, computation.output, signature.subjectID, signature.data).toOpData())
                                    bctx.addAfterCommitHook { myComputations.remove(id) }
                                }
                            }

                            is FailedComputation -> {
                                if (computation.isFast) {
                                    logger.warn("Request id [$id] of type [${computation.type}] was failed but not committed, removing")
                                    myComputations.remove(id)
                                } else {
                                    logger.info("Submitting failed response for request id [$id] of type [${computation.type}]")
                                    val signature = sigMaker.signDigest(hash(id, blockchainRID.toHex(), bctx.height))
                                    add(FailureOp(id, computation.type, computation.input, computation.errorMessage, signature.subjectID, signature.data).toOpData())
                                    bctx.addAfterCommitHook { myComputations.remove(id) }
                                }
                            }
                        }
                    }

                    if (computeClusterTimeoutSeconds > 0) {
                        for (request in module.query(bctx, GET_TAKEN_REQUESTS, gtv(mapOf())).asArray().map { it.toObject<ComputeRequest>() }) {
                            if (isComputeClusterTimeout(request.takenTimestamp, bctx.timestamp)) {
                                logger.warn("Computation of request id [${request.id}] of type [${request.type}] not reported by back by computing node after $computeClusterTimeoutSeconds seconds")
                                add(ClusterTimeoutOp(request.id, request.type, request.input).toOpData())
                            }
                        }
                    }
                }
            }

            SpecialTransactionPosition.End ->
                return buildList {
                    val takenRequests: MutableMap<String, Int> = mutableMapOf()
                    for (request in module.query(bctx, GET_REQUESTS, gtv(mapOf())).asArray().map { it.toObject<ComputeRequest>() }) {
                        val takenRequestsOfThisType = takenRequests.getOrDefault(request.type, 0)
                        if (takenRequestsOfThisType >= concurrency) break

                        val (engine, computer) = getEngineAndComputer(request.id, request.type) ?: continue

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

                        if (myComputations.putIfAbsent(request.id, TakenComputation(request.type, request.input)) != null) continue
                        logger.info("Taking request id [${request.id}] of type [${request.type}]${if (computer == null) " fast" else ""}")
                        val signature = sigMaker.signDigest(hash(request.id, blockchainRID.toHex(), bctx.height))
                        add(RequestTakenOp(request.id, request.type, nodePubkey, signature.data).toOpData())
                        takenRequests[request.type] = takenRequestsOfThisType + 1

                        if (computer == null) {
                            try {
                                val (output, duration) = doCompute(request, engine, bctx, now, periodLength)
                                logger.info("Computation of request id [${request.id}] of type [${request.type}] finished in $duration, submitting successful response")
                                myComputations.replace(request.id, FinishedComputation(request.type, request.input, output, isFast = true))
                                add(ResponseOp(request.id, request.type, request.input, output, signature.subjectID, signature.data).toOpData())
                            } catch (e: UserMistake) {
                                logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed: ${e.message}, submitting failed response")
                                val errorMessage = e.message ?: "Unknown error"
                                myComputations.replace(request.id, FailedComputation(request.type, request.input, errorMessage, isFast = true))
                                add(FailureOp(request.id, request.type, request.input, errorMessage, signature.subjectID, signature.data).toOpData())
                            } catch (e: Exception) {
                                logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed unexpectedly, submitting failed response: $e", e)
                                val errorMessage = "Unknown error"
                                myComputations.replace(request.id, FailedComputation(request.type, request.input, errorMessage, isFast = true))
                                add(FailureOp(request.id, request.type, request.input, errorMessage, signature.subjectID, signature.data).toOpData())
                            }
                            bctx.addAfterCommitHook { myComputations.remove(request.id) }
                        } else {
                            bctx.addAfterCommitHook {
                                val timeoutFuture = AtomicReference<ScheduledFuture<*>?>()
                                val future = computer.submit {
                                    withLoggingContext(mapOf(
                                            CHAIN_IID_TAG to chainID.toString(),
                                            BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                                    )) {
                                        try {
                                            val (output, duration) = doCompute(request, engine, bctx, now, periodLength)
                                            if (!Thread.currentThread().isInterrupted) {
                                                if (myComputations.replace(request.id, StartedComputation(request.type, request.input), FinishedComputation(request.type, request.input, output, isFast = false))) {
                                                    logger.info("Computation of request id [${request.id}] of type [${request.type}] finished in $duration")
                                                } else {
                                                    logger.warn("Finished computation of request id [${request.id}] of type [${request.type}] after $duration could not be stored (state changed)")
                                                }
                                            } else {
                                                logger.debug { "Computation of request id [${request.id}] of type [${request.type}] interrupted" }
                                                myComputations.replace(request.id, FailedComputation(request.type, request.input, "Computation timed out after $computeTimeoutSeconds seconds", isFast = false))
                                            }
                                        } catch (_: InterruptedException) {
                                            logger.debug { "Computation of request id [${request.id}] of type [${request.type}] interrupted with exception" }
                                            myComputations.replace(request.id, FailedComputation(request.type, request.input, "Computation timed out after $computeTimeoutSeconds seconds", isFast = false))
                                        } catch (e: UserMistake) {
                                            logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed: ${e.message}")
                                            myComputations.replace(request.id, FailedComputation(request.type, request.input, e.message
                                                    ?: "Unknown error", isFast = false))
                                        } catch (e: Exception) {
                                            logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed unexpectedly: $e", e)
                                            myComputations.replace(request.id, FailedComputation(request.type, request.input, "Unknown error", isFast = false))
                                        } finally {
                                            timeoutFuture.get()?.cancel(false)
                                        }
                                    }
                                }
                                myComputations.replace(request.id,
                                        TakenComputation(request.type, request.input),
                                        StartedComputation(request.type, request.input))
                                if (computeTimeoutSeconds > 0 && !future.isDone) {
                                    timeoutFuture.set(timeouter.schedule({
                                        withLoggingContext(mapOf(
                                                CHAIN_IID_TAG to chainID.toString(),
                                                BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                                        )) {
                                            if (myComputations.replace(request.id,
                                                            StartedComputation(request.type, request.input),
                                                            FailedComputation(request.type, request.input, "Computation timed out after $computeTimeoutSeconds seconds", isFast = false))) {
                                                logger.warn("Computation of request id [${request.id}] of type [${request.type}] timed out after $computeTimeoutSeconds seconds")
                                            }
                                            future.cancel(true) // interrupt the compute thread
                                        }
                                    }, computeTimeoutSeconds, TimeUnit.SECONDS))
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun doCompute(request: ComputeRequest, engine: HybridComputeEngine, bctx: BlockEContext, now: Instant, periodLength: Duration): TimedValue<Gtv> {
        logger.info("Starting computation of request id [${request.id}] of type [${request.type}]...")
        val (outputPointsConsumed, duration) = measureTimedValue {
            if (engine is DatabaseAwareHybridComputeEngine) {
                withReadConnection(sharedStorage, chainID) { ctx: EContext ->
                    engine.compute(ctx, request.input)
                }
            } else {
                engine.compute(request.input)
            }
        }
        val (output, pointsConsumed) = outputPointsConsumed
        container?.let {
            dbOperations.incrementPoints(bctx, container = it, type = request.type,
                    containerCreationTime = containerCreationTime, now = now,
                    periodLength = periodLength,
                    pointsConsumed = pointsConsumed)
        }
        return TimedValue(output, duration)
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        if (!isFullyLoaded() && ops.isNotEmpty()) {
            throw UserMistake("Engine(s) not loaded yet, returning false from validateSpecialOperations")
        }

        val takenComputations = mutableSetOf<String>()
        val nonDbAwareValidations = mutableListOf<Future<String>>()
        val dbAwareValidations = mutableListOf<Pair<ResponseOp, DatabaseAwareHybridComputeEngine>>()
        for (op in ops) {
            when (op.opName) {
                RequestTakenOp.OP_NAME -> {
                    val request = RequestTakenOp.fromOpData(op)
                    validateSignature(RequestTakenOp.OP_NAME, bctx, request.id, request.type, request.processedBy, request.signatureData)
                    if (!myComputations.contains(request.id)) {
                        bctx.addAfterCommitHook {
                            otherNodesPendingComputations.add(request.id)
                        }
                    }
                    takenComputations.add(request.id)
                }

                ResponseOp.OP_NAME -> {
                    val response = ResponseOp.fromOpData(op)
                    validateSignature(ResponseOp.OP_NAME, bctx, response.id, response.type, response.signatureSubjectId, response.signatureData)

                    if (!takenComputations.contains(response.id)) {
                        val takenComputeRequest = getTakenRequestById(bctx, response.id)
                        if (takenComputeRequest != null) {
                            if (!response.signatureSubjectId.contentEquals(takenComputeRequest.processedBy.data)) {
                                throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] failed: unexpected signer: ${response.signatureSubjectId.toHex()}")
                            }
                        } else {
                            throw UserMistake("Validate of response for id [${response.id}] of type [${response.type}] failed: Taken request not found")
                        }
                    }

                    val engine = getEngine(response.id, response.type)

                    if (myComputations.containsKey(response.id) && response.signatureSubjectId.contentEquals(nodePubkey)) {
                        val localComputation = myComputations[response.id]
                        if (localComputation is FinishedComputation) {
                            if (localComputation.output != response.output) {
                                throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] failed: local output does not match operation output.")
                            }
                            logger.debug { "Skipping validation of response for id [${response.id}] of type [${response.type}] on block builder node" }
                        } else {
                            throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] failed: local computation state is ${localComputation?.javaClass?.simpleName}, expected FinishedComputation.")
                        }
                    } else {
                        if (engine is DatabaseAwareHybridComputeEngine) {
                            dbAwareValidations.add(response to engine)
                        } else {
                            nonDbAwareValidations.add(validator.submit(Callable {
                                withLoggingContext(mapOf(
                                        CHAIN_IID_TAG to chainID.toString(),
                                        BLOCKCHAIN_RID_TAG to blockchainRID.toHex()
                                )) {
                                    try {
                                        logger.info("Starting validation for request id [${response.id}] of type [${response.type}]...")
                                        val duration = measureTime {
                                            engine.validate(response.input, response.output)
                                        }
                                        if (!Thread.currentThread().isInterrupted) {
                                            logger.info("Validation of response for id [${response.id}] of type [${response.type}] succeeded in $duration")
                                            return@Callable response.id
                                        } else {
                                            throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] timed out")
                                        }
                                    } catch (_: InterruptedException) {
                                        throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] timed out with exception")
                                    } catch (e: UserMistake) {
                                        throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] failed: ${e.message}")
                                    } catch (e: Exception) {
                                        throw UserMistake("Validation of response for id [${response.id}] of type [${response.type}] failed unexpectedly: $e", e)
                                    }
                                }
                            }))
                        }
                    }
                }

                FailureOp.OP_NAME -> {
                    val failure = FailureOp.fromOpData(op)
                    validateSignature(ResponseOp.OP_NAME, bctx, failure.id, failure.type, failure.signatureSubjectId, failure.signatureData)

                    if (!takenComputations.contains(failure.id)) {
                        val takenComputeRequest = getTakenRequestById(bctx, failure.id)
                        if (takenComputeRequest != null) {
                            if (!failure.signatureSubjectId.contentEquals(takenComputeRequest.processedBy.data)) {
                                throw UserMistake("Validation of failure for id [${failure.id}] of type [${failure.type}] failed: unexpected signer: ${failure.signatureSubjectId.toHex()}")
                            }
                        } else {
                            throw UserMistake("Validate of failure for id [${failure.id}] of type [${failure.type}] failed: Taken request not found")
                        }
                    }

                    getEngine(failure.id, failure.type)

                    bctx.addAfterCommitHook {
                        otherNodesPendingComputations.remove(failure.id)
                    }
                }

                ClusterTimeoutOp.OP_NAME -> {
                    val clusterTimeoutOp = ClusterTimeoutOp.fromOpData(op)
                    getEngine(clusterTimeoutOp.id, clusterTimeoutOp.type)
                    val request = getTakenRequestById(bctx, clusterTimeoutOp.id)
                    if (request == null || !isComputeClusterTimeout(request.takenTimestamp, bctx.timestamp)) {
                        throw UserMistake("Validation of cluster timeout for id [${clusterTimeoutOp.id}] of type [${clusterTimeoutOp.type}] failed")
                    }
                    bctx.addAfterCommitHook {
                        otherNodesPendingComputations.remove(clusterTimeoutOp.id)
                    }
                }

                else -> {
                    throw UserMistake("Unexpected operation: ${op.opName}")
                }
            }
        }

        for ((response, engine) in dbAwareValidations) {
            try {
                logger.info("Starting DB aware validation for request id [${response.id}] of type [${response.type}]...")
                val duration = measureTime {
                    engine.validate(bctx, response.input, response.output)
                }
                logger.info("DB aware validation for request id [${response.id}] of type [${response.type}] succeeded in $duration")
                bctx.addAfterCommitHook {
                    otherNodesPendingComputations.remove(response.id)
                }
            } catch (e: UserMistake) {
                throw UserMistake("DB aware validation for request id [${response.id}] of type [${response.type}] failed: ${e.message}")
            } catch (e: Exception) {
                throw UserMistake("DB aware validation for request id [${response.id}] of type [${response.type}] failed unexpectedly: $e", e)
            }
        }

        return nonDbAwareValidations.all {
            try {
                val id = it.get()
                bctx.addAfterCommitHook {
                    otherNodesPendingComputations.remove(id)
                }
                true
            } catch (e: CancellationException) {
                throw UserMistake("Validation cancelled unexpectedly: ${e.message}")
            } catch (e: InterruptedException) {
                throw UserMistake("Validation interrupted unexpectedly: ${e.message}")
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is UserMistake) {
                    throw cause
                } else {
                    throw UserMistake("Validation failed unexpectedly: $e", e)
                }
            }
        }
    }

    private fun isFullyLoaded() = (loaded.get() >= engines.size)

    override fun shutdown() {
        timeouter.shutdownNow()
        loader.shutdownNow()
        for ((_, computer) in engines.values) {
            computer?.shutdownNow()
        }
        validator.shutdownNow()
        for ((engine, _) in engines.values) {
            if (engine is Shutdownable) {
                logger.info("Shutting down engine ${engine.name}...")
                val duration = measureTime {
                    (engine as Shutdownable).shutdown()
                }
                logger.info("Engine ${engine.name} shutdown in $duration")
            }
        }
        logger.info("Shutting down executors")
        if (!timeouter.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Timeouter did not terminate in time")
        }
        if (!loader.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Loader did not terminate in time")
        }
        for ((engine, computer) in engines.values) {
            if (computer != null) {
                if (!computer.awaitTermination(1, TimeUnit.SECONDS)) {
                    logger.warn("Computer ${engine.name} did not terminate in time")
                }
            }
        }
        if (!validator.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Validator did not terminate in time")
        }
        myComputations.filterValues { it is StartedComputation }.let {
            if (it.isNotEmpty()) {
                logger.warn("${it.size} computations was not finished: ${it.keys.joinToString(", ")}")
            }
        }
        myComputations.filterValues { it is FinishedComputation || it is FailedComputation }.let {
            if (it.isNotEmpty()) {
                logger.warn("${it.size} finished computations was not reported: ${it.keys.joinToString(", ")}")
            }
        }
        logger.info("Shut down complete")
    }

    fun isComputeClusterTimeout(takenTimestamp: Long, now: Long) =
            (takenTimestamp > 0) && (takenTimestamp + computeClusterTimeoutSeconds * 1000 < now)

    private fun getEngine(id: String, type: String): HybridComputeEngine = getEngineAndComputer(id, type)?.first
            ?: throw UserMistake("Unknown type: $type for id [$id]")

    private fun getEngineAndComputer(id: String, type: String): Pair<HybridComputeEngine, ExecutorService?>? {
        val engineAndComputer = engines[type]
        if (engineAndComputer == null) {
            logger.warn("No engine found for request id [$id] of type [$type]")
            return null
        }
        return engineAndComputer
    }

    private fun validateSignature(opName: String, bctx: BlockEContext, id: String, type: String, subjectId: ByteArray, signatureData: ByteArray) {
        if (!cs.verifyDigest(hash(id, blockchainRID.toHex(), bctx.height), Signature(subjectId, signatureData))) {
            throw UserMistake("Validate $opName operation failed for request id [${id}] of type [${type}]: Invalid signature.")
        }
    }

    internal fun hash(id: String, blockchainRID: String, height: Long) = gtv(gtv(id), gtv(blockchainRID), gtv(height)).merkleHash(GtvMerkleHashCalculatorV2(cs))

    private fun getTakenRequestById(ctx: EContext, id: String): ComputeRequest? {
        val request = module.query(ctx, GET_TAKEN_REQUEST, gtv("id" to gtv(id)))
        return if (request.isNull()) null else request.toObject<ComputeRequest>()
    }

    private fun getRequestById(ctx: EContext, id: String): ComputeRequest? {
        val request = module.query(ctx, GET_REQUEST, gtv("id" to gtv(id)))
        return if (request.isNull()) null else request.toObject<ComputeRequest>()
    }
}
