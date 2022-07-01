package net.postchain.mc.test

import assertk.assertions.isEqualTo
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths
import kotlin.test.*

class Directory1Test : ManagedModeTest() {

    override fun chainConfSnippet(): String {
        val module = "directory1"

        return """
            <chains>
                <chain name="manager" iid="0">
                    <config height="0" add-dependencies="false">
                        <app module="$module">
                            <args module="$module">
                                <arg key="initial_provider"><bytea>${KeyPairHelper.pubKeyHex(providerKey)}</bytea></arg>
                            </args>
                        </app>
                        <gtv path="signers">
                            <array>
                                <bytea>${KeyPairHelper.pubKeyHex(node0BlockSignerKey)}</bytea>
                            </array>
                        </gtv>
                    </config>
                </chain>
            </chains>
        """.trimIndent()
    }

    override fun cliExecution(cliConfig: ClientConfig): CliExecution {
        return CliExecutionD1(cliConfig)
    }

    override val provExecutor = CliExecutionD1(provConfig)
    override val prov2Executor = CliExecutionD1(prov2Config)

    /*
    * The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. Operation init() registers and enables the module argument
    * `initial_provider` as provider. So that we have an initial voter.
    * */
    @BeforeEach
    fun setup() {
        val resourceDirectory = Paths.get("target", "directory1", "rell")
        blockchain0ConfigGtv = run(runXmlFile(), resourceDirectory.toFile())
        doAndBuildBlocks(provConfig, provExecutor.initAsync())
    }

    @Test
    fun testProposeEnableDisableProvider() {
        //First provider adds node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //Then proposes a second provider to system cluster. Includes also add it to system voter_set.
        addSystemProv2()
        assertProviderEnabled(prov2Config.pubKey)

        // The new provider adds node 1 to system cluster. It becomes automatically signer of bcs in cluster. TODO: Start as
        //  replica and once it is in sync make it signer, (to not cause a potential blockbuilding stop.)
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, systemClusterName)

        //First provider proposes Disable prov2. Prov2 agrees:
        doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderAsync(prov2Config.pubKey))
        val id = assertProposalTypeAndGetRowid("provider_state")
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))
        assertProviderDisabled(prov2Config.pubKey)
    }

    @Test
    fun testTransferActionPoints() {
        //First provider adds node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //Then proposes a second provider to system cluster. Includes also add it to system voter_set.
        addSystemProv2()
        val myAP = provExecutor.listProvidersActionPoints(provConfig.pubKey)
        val othersAP = provExecutor.listProvidersActionPoints(prov2Config.pubKey)
        val amount: Long = 20
        doAndBuildBlocks(provConfig, provExecutor.transferActionPointsAsync(prov2Config.pubKey, amount))
        val othersAPAfter = provExecutor.listProvidersActionPoints(prov2Config.pubKey)
        assertEquals(othersAP + amount, othersAPAfter)
        val myAPAfter = provExecutor.listProvidersActionPoints(provConfig.pubKey)
        assertEquals(myAP - amount - 1, myAPAfter)
    }

    /**
     * Add provider prov2 as system provider. Includes proposeEnable and promoting to system: active = true, system = true
     */
    private fun addSystemProv2() {
        doAndBuildBlocks(provConfig, provExecutor.registerProviderAsync(prov2Config.pubKey, 1L))
        doAndBuildBlocks(provConfig, provExecutor.addProviderToClusterAsync(prov2Config.pubKey, systemClusterName))
        doAndBuildBlocks(provConfig, provExecutor.proposeEnableProviderAsync(prov2Config.pubKey))
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemAsync(prov2Config.pubKey, true))
        assertProviderData(prov2Config.pubKey, "", true)
    }

    /**
     * Two active providers. Prov2 proposes demotion of Prov1. Prov1 votes no.
     */
    @Test
    fun testProposeDegradeProviderVoteNo() {

        addSystemProv2()
        // Prov2 proposes degradation/demotion of prov1.
        doAndBuildBlocks(provConfig, prov2Executor.proposeProviderIsSystemAsync(provConfig.pubKey, false))

        // Prov votes no
        voteNo("provider_is_system")
        val listVoterset = provExecutor.listVoterSetMembers(voterSetSystemP)
        assertEquals(2, listVoterset.size)
    }

    /**
     * New voter set with two providers and SYSTEM_P as governor. Create and update members. Change governor.
     */
    @Test
    fun testVoterSet() {

        addSystemProv2()

        val voterSetName = "Ellen"
        val providers_list = "${provConfig.pubKey},${prov2Config.pubKey}"
        doAndBuildBlocks(
                provConfig, provExecutor.createVoterSetAsync(
                voterSetName, providers_list, 0,
                voterSetSystemP
        )
        )
        assertAdded("get_voter_set", "name", GtvString(voterSetName))
        val listVotersets = provExecutor.listVoterSets()
        assertEquals(voterSetName, listVotersets[2].asString())
        assertEquals(voterSetSystemP, provExecutor.getVoterSetGovernor(voterSetName))

        var members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubKey, prov2Config.pubKey), members.map { it.asByteArray().toHex() })


        //remove prov2 from Ellen. Note that with two providers in governance set, both must be OK with the member update.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetMemberAsync(voterSetName, prov2Config.pubKey, false))
        var id = assertProposalTypeAndGetRowid("voter_set_provider")
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))
        members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubKey), members.map { it.asByteArray().toHex() })

        //Make Ellen her own governor.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetGovernorAsync(voterSetName, voterSetName))
        id = assertProposalTypeAndGetRowid("voter_set_governor")
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))

        //add prov2 to voter set Ellen again. Since now only one member, no voting is needed for this proposal to be applied.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetMemberAsync(voterSetName, prov2Config.pubKey, true))
        members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubKey, prov2Config.pubKey), members.map { it.asByteArray().toHex() })
    }

    @Test
    fun testProposeContainerAndLimits() {
        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add new container to system cluster
        val containerName = "container1"
        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeContainerAsync(containerName, systemClusterName, voterSetSystemP)
        )
        assertAdded("get_container", "name", GtvString(containerName))

        val default = provExecutor.listContainerLimits(containerName)
        assertEquals(3, default.size)

        //Now updated container resource limits and check result
        proposeAndAssertContainerLimits(
                containerName,
                mapOf("ramm" to 123L),
                mapOf("ram" to 100L, "cpu" to 100L, "storage" to 100L)
        )

        proposeAndAssertContainerLimits(
                containerName,
                mapOf("ram" to 123L),
                mapOf("ram" to 123L, "cpu" to 100L, "storage" to 100L)
        )

        val limits = mapOf("ram" to 123L, "cpu" to 456L, "storage" to 789L)
        proposeAndAssertContainerLimits(containerName, limits, limits)
    }

    private fun proposeAndAssertContainerLimits(
            containerName: String,
            limits: Map<String, Long>,
            expected: Map<String, Long>
    ) {
        doAndBuildBlocks(provConfig, provExecutor.proposeContainerLimitsAsync(containerName, limits))
        var updated = provExecutor.listContainerLimits(containerName)
        assertEquals(expected, updated)
    }

    @Test
    fun testProposeClusterLimits() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val clusterName = "Vera"
        val providers_list = provConfig.pubKey
        //create cluster, initial providers added
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                clusterName, providers_list,
                voterSetSystemP, voterSetSystemP
        )
        )

        var limits = mapOf("ramm" to 123L)
        var expected = mapOf("ram" to 100L, "cpu" to 100L, "storage" to 100L)
        proposeAndAssertClusterLimits(clusterName, limits, expected)

        limits = mapOf("ram" to 123L)
        expected = mapOf("ram" to 123L, "cpu" to 100L, "storage" to 100L)
        proposeAndAssertClusterLimits(clusterName, limits, expected)

        limits = mapOf("ram" to 123L, "cpu" to 456L, "storage" to 789L)
        proposeAndAssertClusterLimits(clusterName, limits, limits)
    }

    private fun proposeAndAssertClusterLimits(
            clusterName: String,
            limits: Map<String, Long>,
            expected: Map<String, Long>
    ) {
        doAndBuildBlocks(provConfig, provExecutor.proposeClusterLimitsAsync(clusterName, limits))
        var updated = provExecutor.listClusterLimits(clusterName)
        assertEquals(expected, updated)
    }

    @Test
    fun testGetContainersForNode() {
        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add new container to system cluster
        val containerName = "container1"
        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeContainerAsync(containerName, systemClusterName, voterSetSystemP)
        )
        val containerList = prov2Executor.listContainersForNode(nodes[0].pubKey)
        assertEquals(2, containerList.size)
    }

    @Test
    fun testGetBlockchainsForContainer() {
        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        val bcs = prov2Executor.listBlockchainsForContainer(systemContainerName)
        assertEquals(1, bcs.size)
        assertEquals(nodes[0].getBlockchainRid(0)!!.toHex(), bcs[0].toHex())
    }

    @Test
    fun testGetContainerForBlockchain() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        val chain0Brid = nodes[0].getBlockchainRid(0)!!
        val actualContainer = prov2Executor.getContainerForBlockchain(chain0Brid.toHex())

        assertEquals(systemContainerName, actualContainer)
    }

    @Test
    fun testGetContainerForUnknownBlockchain() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        assertThrows<UserMistake> {
            prov2Executor.getContainerForBlockchain(BlockchainRid.ZERO_RID.toHex())
        }
    }

    @Test
    fun testProposeAddBlockchainXmlWithDependency() {

        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add new container to system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.proposeContainerAsync(container1, systemClusterName, voterSetSystemP))
        //propose new bc in new container:
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1))
        assertEquals(2, provExecutor.listBlockchains(false).size)

        //test building blocks for new bc
        buildBlock(100, 4)

        //add yet another bc, dependent on previous one
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlDependencyFile, "xml", container1))
        val listOfBcs = provExecutor.listBlockchains(false)
        val listOfDependencies = provExecutor.listBlockchainDependencies(listOfBcs[2].toHex(), 0)

        assertEquals(1, listOfDependencies.size)
        assertEquals(listOfBcs[1].toHex(), listOfDependencies[0].first.toHex())
        assertEquals(container1, listOfDependencies[0].second)

        //Now make sure that you cannot delete a bc that someone else is dependent on
        doAndBuildBlocks(provConfig, provExecutor.proposeDeleteBlockchainAsync(listOfBcs[1].toHex()))
        assertEquals(3, provExecutor.listBlockchains(false).size)
    }

    /**
     * Includes creating, listing of clusters as well as updating the cluster providers. And updating deployer.
     */
    @Test
    fun testCluster() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val newClusterName = "Vera"
        val providers_list = provConfig.pubKey
//        val providers_list = "${provConfig.pubKey},${provConfig.pubKey}"
        //create cluster, initial providers added
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                newClusterName, providers_list,
                voterSetSystemP, voterSetSystemP
        )
        )
        assertAdded("get_cluster", "name", GtvString(newClusterName))

        var clusters = provExecutor.listClustersForProvider(provConfig.pubKey)
        assertEquals(listOf(systemClusterName, newClusterName), clusters)

        doAndBuildBlocks(provConfig, provExecutor.registerProviderAsync(prov2Config.pubKey, 0))
        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeClusterProviderAsync(newClusterName, prov2Config.pubKey, add = true)
        )
        clusters = provExecutor.listClustersForProvider(prov2Config.pubKey)
        assertEquals(listOf(newClusterName), clusters)

        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeClusterProviderAsync(newClusterName, prov2Config.pubKey, add = false)
        )
        clusters = provExecutor.listClustersForProvider(prov2Config.pubKey)
        assertEquals(listOf(), clusters)

        //change deployer
        doAndBuildBlocks(provConfig, provExecutor.proposeClusterDeployerAsync(newClusterName, voterSetSystem))
        val clusterInfo = provExecutor.getClusterInfo(newClusterName)
        println(clusterInfo.asDict())
        assertk.assert(clusterInfo["deployer"]?.asString()).isEqualTo(voterSetSystem)

    }

    @Test
    fun testProposeConfigurationAcceptGtv() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.brid, bcConfigGtvFile,
                20L, "gtv", false
        )
        )
        assertNextConfiguration(provConfig, 20L)

        //force == false: only configs for heights > next config height can be added:
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.brid, bcConfigGtvFile,
                18L, "gtv", false
        )
        )
        assertNextConfiguration(provConfig, 20L)

        //force == true: OK to add configs at all heights > current height
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.brid, bcConfigGtvFile,
                18L, "gtv", true
        )
        )
        val conf18Gtv = assertNextConfiguration(provConfig, 18L)

        //force == true: OK to override a configuration
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.brid, bcConfig1xmlFile,
                18L, "xml", true
        )
        )
        val conf18GXml = assertNextConfiguration(provConfig, 18L)
        assertNotEquals(conf18Gtv, conf18GXml)
    }

    @Test
    fun testProposeConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.brid, bcConfig1xmlFile,
                20L, "xml", false
        )
        )
        assertNextConfiguration(provConfig, 20L)
    }

    private fun voteNo(proposalType: String) {
        val id = assertProposalTypeAndGetRowid(proposalType)
        doAndBuildBlocks(provConfig, provExecutor.voteAsync(id, false))
    }

    @Test
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //includes prov adds prov2 to system cluster
        addSystemProv2()

        // Add node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)

        // Get next configuration height after adding new node as blockchain's signer
        // expected next congiguration height = -1 + init + 3*addNode + porposeEnableProv + proposeBlockhain + addprov2toCluster + addNode + 5 = 12 (with vote included in proposal)
        assertNextConfiguration(provConfig, 12L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(2)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(Duration.ONE_SECOND).until {
                doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderAsync(prov2Config.pubKey))
                true
            }
        }
    }

    @Test
    fun testRemoveBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        addSystemProv2()
        // Prov2 adds node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)

        doAndBuildBlocks(prov2Config, provExecutor.removeNodeAsync(node1Pubkey))
        val listBlockchainSigners = provExecutor.listBlockchainSigners(provConfig.brid)
        assertEquals(1, listBlockchainSigners.size)
    }

    @Test
    fun testPauseBlockchain() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.proposeContainerAsync(container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1))

        //pause new bc
        var bcs = provExecutor.listBlockchains(false)
        val bridToPause = bcs[1].toHex()
        doAndBuildBlocks(provConfig, provExecutor.proposePauseBlockchainAsync(bridToPause))

        bcs = provExecutor.listBlockchains(true)
        assertEquals(2, bcs.size)
        bcs = provExecutor.listBlockchains(false)
        assertEquals(1, bcs.size)

        // try building blocks of pause bc
        assertBuildBlockFailure()

        doAndBuildBlocks(provConfig, provExecutor.proposeUnPauseBlockchainAsync(bridToPause))
        bcs = provExecutor.listBlockchains(false)
        assertEquals(2, bcs.size)

        // build after unpause
        buildBlock(100, 3)
    }

    @Test
    fun testDeleteBlockchain() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.proposeContainerAsync(container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1))

        var bcs = provExecutor.listBlockchains(false)
        assertEquals(2, bcs.size)

        //delete new bc
        doAndBuildBlocks(provConfig, provExecutor.proposeDeleteBlockchainAsync(bcs[1].toHex()))

        bcs = provExecutor.listBlockchains(true)
        assertEquals(1, bcs.size)

        // try building blocks of deleted bc
        assertBuildBlockFailure()
    }

    private fun assertBuildBlockFailure() {
        var buildFailed = false
        try {
            buildBlock(100, 2)
        } catch (e: Exception) {
            buildFailed = true
        }
        assertTrue(buildFailed)
    }

    @Test
    fun testListBlockchainsForNode() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        var listBlockchains = provExecutor.listBlockchainsForNode(node1Pubkey)
        assertEquals(0, listBlockchains.size)

        listBlockchains = provExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)
    }


    @Test
    fun testGetBlockchainLastHeight() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val h = provExecutor.getBlockchainLastHeight(provConfig.brid)
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 + vote = 3
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 = 2 (vote included in proposal)
        assertEquals(2, h)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val blockchain = provExecutor.getBlockchainConfiguration(provConfig.brid, 0L)
        assert(blockchain.isNotEmpty())
        val modules = GtvFactory.decodeGtv(blockchain).asDict()["gtx"]?.get("modules")
        assertEquals("net.postchain.rell.module.RellPostchainModuleFactory", modules?.get(0)?.asString())
    }

    @Test
    fun testGetNodeListVersion() {
        addNode0(provConfig, "")
        val version = provExecutor.getNodeListVersion()
        assertTrue(version > 0)
    }

    @Test
    fun testRemoveNode() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        // Add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")
        var nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertTrue(nodeInfo.get("active")!!.asBoolean())

        // Remove node1
        doAndBuildBlocks(provConfig, provExecutor.removeNodeAsync(node1Pubkey))

        nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertFalse(nodeInfo.get("active")!!.asBoolean())
    }

    @Test
    fun testListNodesWithProvider() {
        addNode0(provConfig, "")
        // Add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")
        assertListNodes()
    }

    @Test
    fun testListBlockchains() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val listBlockchains = provExecutor.listBlockchains(false)
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testAddBlockchainReplicas() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addBlockchainReplicaAsync(provConfig.brid, node1Pubkey), 5)
        assertBlockchainReplica(provConfig, node1Pubkey, node1Host, node1Port)

        val listBlockchainReplicas = provExecutor.listBlockchainReplicas(provConfig.brid)
        assertEquals(1, listBlockchainReplicas.size)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //Add second system provider
        addSystemProv2()

        //prov adds prov2 to system cluster
        doAndBuildBlocks(provConfig, provExecutor.addProviderToClusterAsync(prov2Config.pubKey, "system"))

        // Prov2 adds new node to system cluster => Two blockchain signers in system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, "system")

        val listBlockchainSigners = provExecutor.listBlockchainSigners(provConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    /**
     * Includes also test of listClusters
     */
    @Test
    fun testListContainerReplicas() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //create new cluster Vera.
        val clusterA = "A"
        val clusterB = "B"
        val containerName = "C"
        val providers_list = provConfig.pubKey
        //create two new clusters with initial provider added
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                clusterA, providers_list,
                voterSetSystemP, voterSetSystemP
        )
        )
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                clusterB, providers_list,
                voterSetSystemP, voterSetSystemP
        )
        )

        val clusterList = provExecutor.listClusters().map { it.asString() }
        assertEquals(listOf("system", clusterA, clusterB), clusterList)

        //add a container C to cluster A
        doAndBuildBlocks(provConfig, provExecutor.proposeContainerAsync(containerName, clusterA, voterSetSystemP))
        // add a replica of C in cluster B
        doAndBuildBlocks(provConfig, provExecutor.addContainerReplicaAsync(clusterB, containerName))

        val listContainerReplicas = provExecutor.listContainerReplicas(containerName)
        assertEquals(1, listContainerReplicas.size)
        assertEquals(clusterB, listContainerReplicas[0].asString())
    }

    @Test
    fun testGetProposal() {
        addSystemProv2()
        //Propose degradation of prov2 again
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemAsync(prov2Config.pubKey, false))

        val type = "provider_is_system"
        val id = assertProposalTypeAndGetRowid(type)
        val proposal = provExecutor.getProposal(id).asDict()
        val actualType = (proposal["proposal_type"] as GtvString).string
        val propid = (proposal["rowid"] as GtvInteger).asInteger()
        val timestamp = (proposal["timestamp"] as GtvInteger).asInteger()
        val proposedBy = proposal["proposed_by"]!!.asByteArray().toHex()

        assertEquals(provConfig.pubKey, proposedBy, "wrong proposed_by")
        assertEquals(id, propid, "wrong idx")
        assertEquals(type, actualType, "Wrong proposal type")
        assertNotEquals(0, timestamp, "timestamp is 0")

    }

    //    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: String): Long {
        val proposals = provExecutor.listProposalsSince(0)
        val type = (proposals[0].asDict()["proposal_type"] as GtvString).string
        assertEquals(expectedType, type, "Wrong proposal type")
        return (proposals[0].asDict()["rowid"] as GtvInteger).asInteger()
    }

}