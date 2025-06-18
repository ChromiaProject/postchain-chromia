package net.postchain.images.directory1

import mu.KLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

object DiskHelper {

    private val tmpFiles = mutableListOf<Path>()

    fun mkTmpDockerHostDir(): Path {

        val testMountDirectory = System.getenv("TEST_MOUNT_DIRECTORY")
        val dockerHostDir = if (testMountDirectory == null)
            Files.createTempDirectory("postchain-chromia-it-")
        else
            Files.createTempDirectory(Path.of(testMountDirectory), "postchain-chromia-it-")

        dockerHostDir.toFile().deleteOnExit()
        tmpFiles.add(dockerHostDir)
        return dockerHostDir
    }

    fun mkTmpDockerHostDirBasedOnResources(configDir: String, node: String): Path {
        val dir = mkTmpDockerHostDir()
        File(this::class.java.getResource("${configDir}/${node}")!!.toURI()).copyRecursively(dir.toFile(), false)
        return dir
    }

    @OptIn(ExperimentalPathApi::class)
    fun cleanup(testLogger: KLogger) {
        tmpFiles.forEach {
            try {
                it.deleteRecursively()
            } catch (e: Exception) {
                testLogger.warn("Failed to delete ${it.pathString}: ${e.message}")
            }
        }
    }
}