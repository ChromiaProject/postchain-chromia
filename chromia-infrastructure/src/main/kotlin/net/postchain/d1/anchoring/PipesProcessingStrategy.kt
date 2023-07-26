package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.common.reflection.constructorOf
import net.postchain.gtv.Gtv

class PipesProcessingStrategyFactory(private val className: String) {

    fun create(pipes: List<AnchoringPipe>): PipesProcessingStrategy {
        return constructorOf<PipesProcessingStrategy>(className, List::class.java)
                .newInstance(pipes)
    }

    companion object {
        fun fromGtv(gtv: Gtv): PipesProcessingStrategyFactory {
            val className = gtv[KEY_BLOCKCHAIN_CONFIG_ANCHORING]
                    ?.get(KEY_BLOCKCHAIN_CONFIG_ANCHORING_PIPES_PROCESSING_STRATEGY)?.asString()
                    ?: OneByOnePipesProcessingStrategy::class.java.name
            return PipesProcessingStrategyFactory(className)
        }
    }
}

interface PipesProcessingStrategy {
    fun hasNext(): Boolean
    fun nextPipe(): AnchoringPipe
    fun markDone(pipe: AnchoringPipe)
}

class OneByOnePipesProcessingStrategy(private val pipes: List<AnchoringPipe>) : PipesProcessingStrategy {

    private var cur = 0

    override fun hasNext() = cur < pipes.size && tail().any { it.mightHaveNewPackets() }

    override fun nextPipe(): AnchoringPipe {
        if (!hasNext()) throw NoSuchElementException("current is $cur, pipes list size is ${pipes.size}")
        cur += tail().indexOfFirst { it.mightHaveNewPackets() }
        return pipes[cur]
    }

    override fun markDone(pipe: AnchoringPipe) {
        cur++
    }

    private fun tail() = pipes.drop(cur)
}

class FairPipesProcessingStrategy(private val pipes: List<AnchoringPipe>) : PipesProcessingStrategy {

    companion object : KLogging()

    private var cur = 0
    private val done = mutableSetOf<Long>()
    private val hasNext: (AnchoringPipe) -> Boolean = { it.mightHaveNewPackets() && it.chainID !in done }

    override fun hasNext(): Boolean = pipes.any(hasNext)

    override fun nextPipe(): AnchoringPipe {
        if (!hasNext()) throw NoSuchElementException("all pipes are empty")
        cur = tail().indexOfFirst(hasNext).let {
            if (it != -1) cur + it else head().indexOfFirst(hasNext)
        }
        return pipes[cur++]
    }

    override fun markDone(pipe: AnchoringPipe) {
        done.add(pipe.chainID)
    }

    private fun tail() = pipes.drop(cur)
    private fun head() = pipes.take(cur)
}