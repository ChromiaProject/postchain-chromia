package net.postchain.dapp

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer

fun <C : GenericContainer<C>> startContainers(vararg container: C) {
    runBlocking {
        container.map { async { it.start() } }.awaitAll()
    }
}

fun <C : GenericContainer<C>> stopContainers(vararg container: C) {
    runBlocking {
        container.map { async { it.stop() } }.awaitAll()
    }
}