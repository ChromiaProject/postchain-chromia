package net.postchain.hybridcompute

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
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
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.hybridcompute.rell.lib.hybridcompute.ComputeRequest
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUEST
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.TakenComputeRequest
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
import kotlin.concurrent.thread
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class HybridComputeSpecialTransactionExtension : GTXSpecialTxExtension, Shutdownable {
    companion object : KLogging()

    internal lateinit var config: HybridComputeConfig
    internal lateinit var engine: HybridComputeEngine

    private lateinit var module: GTXModule
    private lateinit var loader: Thread
    private val loaded = AtomicBoolean(false)
    var hasDistributedTimeout: Boolean = false

    private lateinit var nodePubkey: ByteArray
    private lateinit var sigMaker: SigMaker
    private lateinit var cs: CryptoSystem
    private lateinit var blockchainRID: BlockchainRid

    override fun getRelevantOps(): Set<String> = setOf(RequestTakenOp.OP_NAME, ResponseOp.OP_NAME, FailureOp.OP_NAME, ClusterTimeoutOp.OP_NAME)

    private val computations = ConcurrentHashMap<String, Computation>() // id -> computation

    private val computer: ExecutorService by lazy {
        ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                ArrayBlockingQueue<Runnable?>(config.concurrency.toInt()),
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
        this.blockchainRID = blockchainRID
    }

    fun initSigMaker(pubKeyByteArray: ByteArray, privKeyByteArray: ByteArray) {
        this.nodePubkey = pubKeyByteArray
        this.sigMaker = cs.buildSigMaker(KeyPair(nodePubkey, privKeyByteArray))
    }

    fun load() {
        var timeoutFuture: ScheduledFuture<*>? = null
        loader = thread(name = "hybridcompute-load") {
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
                timeoutFuture?.cancel(false)
            }
        }
        timeoutFuture = timeouter.schedule({
            logger.warn("Loading timed out after ${config.loadTimeoutSeconds} seconds, interrupting it")
            loader.interrupt()
        }, config.loadTimeoutSeconds, TimeUnit.SECONDS)
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean =
            position == SpecialTransactionPosition.Begin

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        if (position != SpecialTransactionPosition.Begin) return listOf()
        if (!loaded.get()) {
            logger.info("Engine not loaded yet, returning empty list from createSpecialOperations")
            return listOf()
        }
        return buildList {
            for ((id, computation) in computations) {
                when (computation) {
                    is FinishedComputation -> {
                        logger.info("Submitting successful response for request id [$id] of type [${computation.type}]")
                        val signature = sigMaker.signDigest(hash(id, blockchainRID.toHex(), bctx.height))
                        add(ResponseOp(id, computation.type, computation.output, signature.subjectID, signature.data).toOpData())
                        bctx.addAfterCommitHook { computations.remove(id) }
                    }

                    is FailedComputation -> {
                        logger.info("Submitting failed response for request id [$id] of type [${computation.type}]")
                        val signature = sigMaker.signDigest(hash(id, blockchainRID.toHex(), bctx.height))
                        add(FailureOp(id, computation.type, computation.errorMessage, signature.subjectID, signature.data).toOpData())
                        bctx.addAfterCommitHook { computations.remove(id) }
                    }

                    is StartedComputation -> {} // nothing to do
                }
            }

            var takenRequests = 0
            for (request in module.query(bctx, GET_REQUESTS, gtv(mapOf())).asArray().map { it.toObject<ComputeRequest>() }) {
                if (engine.name == request.type) {
                    if (takenRequests < config.concurrency && (computations.putIfAbsent(request.id, StartedComputation(request.type)) == null)) {
                        try {
                            var timeoutFuture: ScheduledFuture<*>? = null
                            val future = computer.submit {
                                logger.info("Starting computation of request id [${request.id}] of type [${request.type}]...")
                                try {
                                    val (output, duration) = measureTimedValue { engine.compute(request.input) }
                                    if (!Thread.currentThread().isInterrupted) {
                                        logger.info("Computation of request id [${request.id}] of type [${request.type}] finished in $duration")
                                        computations.replace(request.id, FinishedComputation(request.type, output))
                                    } else {
                                        logger.debug { "Computation of request id [${request.id}] of type [${request.type}] interrupted" }
                                        computations.replace(request.id, FailedComputation(request.type, "Computation timed out after ${config.computeTimeoutSeconds} seconds"))
                                    }
                                } catch (_: InterruptedException) {
                                    logger.debug { "Computation of request id [${request.id}] of type [${request.type}] interrupted with exception" }
                                    computations.replace(request.id, FailedComputation(request.type, "Computation timed out after ${config.computeTimeoutSeconds} seconds"))
                                } catch (e: UserMistake) {
                                    logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed: ${e.message}")
                                    computations.replace(request.id, FailedComputation(request.type, e.message
                                            ?: "Unknown error"))
                                } catch (e: Exception) {
                                    logger.warn("Computation of request id [${request.id}] of type [${request.type}] failed unexpectedly: $e", e)
                                    computations.replace(request.id, FailedComputation(request.type, "Unknown error"))
                                } finally {
                                    timeoutFuture?.cancel(false)
                                }
                            }
                            timeoutFuture = timeouter.schedule({
                                logger.warn("Computation of request id [${request.id}] of type [${request.type}] timed out after ${config.computeTimeoutSeconds} seconds")
                                computations.replace(request.id, FailedComputation(request.type, "Computation timed out after ${config.computeTimeoutSeconds} seconds"))
                                future.cancel(true) // interrupt the compute thread
                            }, config.computeTimeoutSeconds, TimeUnit.SECONDS)
                            val signature = sigMaker.signDigest(hash(request.id, blockchainRID.toHex(), bctx.height))
                            add(RequestTakenOp(request.id, nodePubkey, signature.data).toOpData())
                            takenRequests++
                        } catch (_: RejectedExecutionException) {
                            computations.remove(request.id)
                        }
                    }
                } else {
                    logger.warn("No engine found for request id [${request.id}] of type [${request.type}]")
                }
            }
            if (hasDistributedTimeout) {
                for (request in module.query(bctx, GET_TAKEN_REQUESTS, gtv(mapOf())).asArray().map { it.toObject<TakenComputeRequest>() }) {
                    val takenTimestamp = request.takenTimestamp
                    if (isComputeClusterTimeout(takenTimestamp, Instant.now().toEpochMilli())) {
                        logger.warn("Computation of request id [${request.id}] of type [${request.type}] not reported by back by computing node after ${config.computeClusterTimeoutSeconds} seconds")
                        add(ClusterTimeoutOp(request.id, request.type).toOpData())
                    }
                }
            }
        }
    }

    fun isComputeClusterTimeout(takenTimestamp: Long, now: Long) =
            takenTimestamp + config.computeClusterTimeoutSeconds * 1000 < now

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
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
                                    engine.validate(response.output)
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
                        if (request.isNull() || !isComputeClusterTimeout(request.toObject<TakenComputeRequest>().takenTimestamp, Instant.now().toEpochMilli())) {
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

    override fun shutdown() {
        timeouter.shutdownNow()
        computer.shutdown()
        if (!loaded.get() && loader.isAlive) {
            logger.warn("Loading not finished yet, interrupting it")
            loader.interrupt()
            loader.join(1000)
        }
        logger.info("Shutting down engine...")
        val duration = measureTime {
            engine.shutdown()
        }
        logger.info("Engine shutdown in $duration")
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
        computations.filterValues { it !is StartedComputation }.let {
            if (it.isNotEmpty()) {
                logger.warn("${it.size} finished computations was not reported: ${it.keys.joinToString(", ")}")
            }
        }
        logger.info("Shut down complete")
    }

    private fun hash(id: String, blockchainRID: String, height: Long) = gtv(gtv(id), gtv(blockchainRID), gtv(height)).merkleHash(GtvMerkleHashCalculatorV2(cs))

    private fun getTakenRequestById(bctx: BlockEContext, id: String) = module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv(id))))
}
