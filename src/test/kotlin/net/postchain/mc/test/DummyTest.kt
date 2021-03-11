package net.postchain.mc.test

import net.postchain.devtools.KeyPairHelper
import net.postchain.mc.cli.chromia0.CliExecutionC0
import org.junit.Test

class DummyTest : RellIntegrationTest() {

    val blockSignerKey = 0
    val adminKey = 1
    val providerKey = 2
    override fun chainConfSnippet(): String{
        val module = "chroma0"

        return """
            <chains>
                <chain name="manager" iid="0">
                    <config height="0" add-dependencies="false">
                        <app module="${module}">
                            <args module="${module}">
                                <arg key="admin"><bytea>${KeyPairHelper.pubKeyHex(adminKey)}</bytea></arg>
                            </args>
                        </app>
                        <gtv path="signers">
                            <array>
                                <bytea>${KeyPairHelper.pubKeyHex(blockSignerKey)}</bytea>
                            </array>
                        </gtv>
                    </config>
                </chain>
            </chains>
        """.trimIndent()
    }

    @Test
    fun testSetup() {
        run("chroma0")

        val adminCliExecution = CliExecutionC0(cliConf(adminKey))
        adminCliExecution.registerProvider(KeyPairHelper.pubKeyHex(providerKey))

        val providerCliExecution = CliExecutionC0(cliConf(providerKey))
        providerCliExecution.addNode(KeyPairHelper.pubKeyHex(blockSignerKey), "localhost", 9999, "")
    }
}
