package net.postchain.d1.dummy

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class TickerService {
    private val counter = AtomicLong(0)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var isRunning = false

    val queue = ConcurrentLinkedQueue<Long>()

    fun start() {
        if (!isRunning) {
            isRunning = true
            scheduler.scheduleWithFixedDelay({
                queue.offer(counter.incrementAndGet())
            }, 0, 1, TimeUnit.SECONDS)
        }
    }

    fun stop() {
        isRunning = false
        scheduler.shutdown()
    }

    fun pruneUpTo(tick: Long) {
        counter.set(tick)
        while (queue.isNotEmpty() && queue.peek() <= tick) {
            queue.poll()
        }
    }
}
