package net.postchain.dapp

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.postchain.images.directory1.testLogger
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import org.testcontainers.containers.GenericContainer
import java.lang.Thread.sleep
import kotlin.random.Random

fun <C : GenericContainer<C>> startContainers(vararg container: C) {

    runBlocking {
        container.map {
            async {
                Unreliables.retryUntilSuccess(3) {
                    try {
                        it.start()
                    } catch (e: ContainerLaunchException) {
                        testLogger.warn(e) { "Failed to start container ${it.containerId}: ${e.message}" }
                        it.stop()
                        sleep(Random.nextLong(200, 8000))
                        throw e
                    }
                }
            }
        }.awaitAll()
    }
}

fun <C : GenericContainer<C>> stopContainers(vararg container: C) {
    runBlocking {
        container.map { async { it.stop() } }.awaitAll()
    }
}