package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.hybridcompute.HybridComputeSpecialTransactionExtension
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.test.appender.ListAppender
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

// Needs to be in separate class to get log capturing working
class HybridComputeLoadFailIT : IntegrationTestSetup() {

    val chainIid = 1

    fun doSystemSetup(nodeCount: Int, bcConfFileName: String): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        Assertions.assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")

        createNodesFromSystemSetup(sysSetup)
        return sysSetup
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `failed loading`() {
        val context = LoggerContext.getContext(false)
        val logger = context.getLogger(HybridComputeSpecialTransactionExtension::class.java)
        val appender = ListAppender("LoadFailure").apply {
            start()
        }
        context.configuration.addLoggerAppender(logger as Logger, appender)

        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test_load_fail.xml")

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertThat(appender.events.map { it.message.toString() })
                    .contains("Loading engine failed: Load failed")
        }
    }
}
