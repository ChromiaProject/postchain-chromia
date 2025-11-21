package net.postchain.hybridcompute.it

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

sealed interface TestEngineBehavior {
    fun encode(): Gtv

    fun compute(input: Gtv): Gtv
    fun validate(input: Gtv, output: Gtv) {
        if (input != output) throw UserMistake("output does not match input")
    }

    companion object {
        fun decode(input: Gtv): TestEngineBehavior {
            val inputArray = input.asArray()
            return when (inputArray[0].asInteger().toInt()) {
                CompleteComputation.TAG -> CompleteComputation(input[1].asInteger().toInt(), input[2].asInteger())
                FailComputation.TAG -> FailComputation(input[1].asInteger().toInt())
                ErrorComputation.TAG -> ErrorComputation(input[1].asInteger().toInt())
                InvalidComputation.TAG -> InvalidComputation(input[1].asInteger().toInt())
                else -> throw IllegalArgumentException("Unknown test engine behavior")
            }
        }
    }
}

class CompleteComputation(val delaySeconds: Int, val data: Long) : TestEngineBehavior {
    companion object {
        const val TAG = 0
    }

    override fun encode(): Gtv = gtv(gtv(TAG.toLong()), gtv(delaySeconds.toLong()), gtv(data))

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

    override fun validate(input: Gtv, output: Gtv) {
        throw UserMistake("Invalid")
    }
}
