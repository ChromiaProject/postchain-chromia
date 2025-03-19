package net.postchain.hybridcompute.it

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.hybridcompute.HybridComputeEngine

sealed interface TestEngineBehavior {
    fun encode(): Gtv

    fun compute(input: Gtv): Gtv
    fun validate(bytes: Gtv) {}

    companion object {
        fun decode(input: Gtv): TestEngineBehavior {
            val inputArray = input.asArray()
            return when(inputArray[0].asInteger().toInt()) {
                CompleteComputation.TAG -> CompleteComputation(input[1].asInteger().toInt())
                FailComputation.TAG -> FailComputation(input[1].asInteger().toInt())
                ErrorComputation.TAG -> ErrorComputation(input[1].asInteger().toInt())
                InvalidComputation.TAG -> InvalidComputation(input[1].asInteger().toInt())
                else -> throw IllegalArgumentException("Unknown test engine behavior")
            }
        }
    }
}

class CompleteComputation(val delaySeconds: Int) : TestEngineBehavior {
    companion object {
        const val TAG = 0
    }

    override fun encode(): Gtv = gtv(gtv(TAG.toLong()), gtv(delaySeconds.toLong()))

    override fun compute(input: Gtv): Gtv {
        Thread.sleep(delaySeconds * 1000L)
        return input
    }
}

class FailComputation(val delaySeconds: Int) : TestEngineBehavior {
    companion object {
        const val TAG = 1
    }

    override fun encode(): Gtv = gtv(gtv(TAG.toLong()), gtv(delaySeconds.toLong()))

    override fun compute(input: Gtv): Gtv {
        Thread.sleep(delaySeconds * 1000L)
        throw UserMistake("Fail")
    }
}

class ErrorComputation(val delaySeconds: Int) : TestEngineBehavior {
    companion object {
        const val TAG = 2
    }

    override fun encode(): Gtv = gtv(gtv(TAG.toLong()), gtv(delaySeconds.toLong()))

    override fun compute(input: Gtv): Gtv {
        Thread.sleep(delaySeconds * 1000L)
        throw RuntimeException("Error")
    }
}

class InvalidComputation(val delaySeconds: Int) : TestEngineBehavior {
    companion object {
        const val TAG = 3
    }

    override fun encode(): Gtv = gtv(gtv(TAG.toLong()), gtv(delaySeconds.toLong()))

    override fun compute(input: Gtv): Gtv {
        Thread.sleep(delaySeconds * 1000L)
        return input
    }

    override fun validate(bytes: Gtv) {
        throw UserMistake("Invalid")
    }
}

@Suppress("unused")
class TestHybridComputeEngine : HybridComputeEngine {
    companion object : KLogging()

    override val name: String = "test"

    private var initialized = false
    private var loaded = false

    private var loadFail = false
    private var loadTimeout = false

    override fun init(blockchainConfig: Gtv, blockchainRID: BlockchainRid) {
        logger.info("init")
        if (blockchainConfig["hybridcompute"]!!.asDict()["load_fail"]?.asBoolean() == true) {
            loadFail = true
        }
        if (blockchainConfig["hybridcompute"]!!.asDict()["load_timeout"]?.asBoolean() == true) {
            loadTimeout = true
        }
        initialized = true
    }

    override fun load() {
        require(initialized) { "Not initialized" }
        logger.info("Load starting")
        Thread.sleep(1000)
        if (loadFail) {
            throw UserMistake("Load failed")
        }
        if (loadTimeout) {
            Thread.sleep(10000)
        }
        logger.info("Load finished")
        loaded = true
    }

    override fun compute(input: Gtv): Gtv {
        require(initialized) { "Not initialized" }
        require(loaded) { "Not loaded" }
        logger.info("Compute starting")
        val behavior = TestEngineBehavior.decode(input)
        val output = behavior.compute(input)
        logger.info("Compute finished")
        return output
    }

    override fun validate(output: Gtv) {
        require(initialized) { "Not initialized" }
        require(loaded) { "Not loaded" }
        logger.info("Validate starting")
        val behavior = TestEngineBehavior.decode(output)
        behavior.validate(output)
        logger.info("Validate finished")
    }

    override fun shutdown() {
        require(initialized) { "Not initialized" }
        logger.info("shutdown")
        initialized = false
    }
}
