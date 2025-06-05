package net.postchain.images.directory1

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

class DiskHelper {
    companion object {

        private val tmpFiles = mutableListOf<Path>()

        fun tmpDockerHostDir(): Path {

            val testMountDirectory = System.getenv("TEST_MOUNT_DIRECTORY")

            val dir = if (testMountDirectory == null)
                Files.createTempDirectory("postchain-chromia-it-")
            else Files.createTempDirectory(Path.of(testMountDirectory), "postchain-chromia-it-")
            dir.toFile().deleteOnExit()
            tmpFiles.add(dir)
            return dir
        }

        fun tmpDirBasedOnResources(configDir: String, node: String): Path {
            val dir = tmpDockerHostDir()
            File(this::class.java.getResource("${configDir}/${node}")!!.toURI()).copyRecursively(dir.toFile(), false)
            return dir
        }

        @OptIn(ExperimentalPathApi::class)
        fun cleanup() {
            tmpFiles.forEach {
                try {
                    it.deleteRecursively()
                } catch (e: Exception) {
                    println("Failed to delete ${it.pathString}: ${e.message}")
                }
            }
        }
    }
}