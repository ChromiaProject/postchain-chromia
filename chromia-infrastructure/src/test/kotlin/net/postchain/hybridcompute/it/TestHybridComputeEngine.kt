package net.postchain.hybridcompute.it

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.hybridcompute.HybridComputeEngine

sealed interface TestEngineBehavior {
    fun encode(): ByteArray

    fun compute(input: ByteArray): ByteArray
    fun validate(bytes: ByteArray) {}

    companion object {
        fun decode(input: ByteArray): TestEngineBehavior {
            return when(input[0]) {
                CompleteComputation.TAG -> CompleteComputation(input[1])
                FailComputation.TAG -> FailComputation(input[1])
                ErrorComputation.TAG -> ErrorComputation(input[1])
                InvalidComputation.TAG -> InvalidComputation(input[1])
                else -> throw IllegalArgumentException("Unknown test engine behavior")
            }
        }
    }
}

class CompleteComputation(val delaySeconds: Byte) : TestEngineBehavior {
    companion object {
        const val TAG: Byte = 0
    }

    override fun encode(): ByteArray = byteArrayOf(TAG, delaySeconds.toByte())

    override fun compute(input: ByteArray): ByteArray {
        Thread.sleep(delaySeconds * 1000L)
        return input
    }
}

class FailComputation(val delaySeconds: Byte) : TestEngineBehavior {
    companion object {
        const val TAG: Byte = 1
    }

    override fun encode(): ByteArray = byteArrayOf(TAG, delaySeconds.toByte())

    override fun compute(input: ByteArray): ByteArray {
        Thread.sleep(delaySeconds * 1000L)
        throw UserMistake("Fail")
    }
}

class ErrorComputation(val delaySeconds: Byte) : TestEngineBehavior {
    companion object {
        const val TAG: Byte = 2
    }

    override fun encode(): ByteArray = byteArrayOf(TAG, delaySeconds.toByte())

    override fun compute(input: ByteArray): ByteArray {
        Thread.sleep(delaySeconds * 1000L)
        throw RuntimeException("Error")
    }
}

class InvalidComputation(val delaySeconds: Byte) : TestEngineBehavior {
    companion object {
        const val TAG: Byte = 3
    }

    override fun encode(): ByteArray = byteArrayOf(TAG, delaySeconds.toByte())

    override fun compute(input: ByteArray): ByteArray {
        Thread.sleep(delaySeconds * 1000L)
        return input
    }

    override fun validate(bytes: ByteArray) {
        throw UserMistake("Invalid")
    }
}

@Suppress("unused")
class TestHybridComputeEngine : HybridComputeEngine {
    companion object : KLogging()

    override val name: String = "test"

    override fun compute(input: ByteArray): ByteArray {
        logger.info("Compute starting")
        val behavior = TestEngineBehavior.decode(input)
        val output = behavior.compute(input)
        logger.info("Compute finished")
        return output
    }

    override fun validate(output: ByteArray) {
        logger.info("Validate starting")
        val behavior = TestEngineBehavior.decode(output)
        behavior.validate(output)
        logger.info("Validate finished")
    }

    override fun shutdown() { }
}
