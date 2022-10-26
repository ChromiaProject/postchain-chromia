package net.postchain.mc.test

import assertk.assert
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.chain0.common.proposal.proposeBlockchainActionOperation
import net.postchain.chain0.common.proposal.ProposalType
import net.postchain.chain0.common.proposal.getProposal
import net.postchain.chain0.directory1.initOperation
import net.postchain.chain0.model.BlockchainAction
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.types.RowId
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.common0.CliExecution
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths
import java.util.Comparator.naturalOrder
import kotlin.test.*

class Directory1IT : ManagedModeTest() {

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

    override fun cliExecution(cliConfig: PostchainClientConfig): CliExecution {
        return CliExecution(cliConfig)
    }

    override val provExecutor by lazy { CliExecution(provConfig) }
    override val prov2Executor by lazy { CliExecution(prov2Config) }

    /*
    * The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. Operation init() registers and enables the module argument
    * `initial_provider` as provider. So that we have an initial voter.
    * */
    @BeforeEach
    fun setup() {
        val resourceDirectory = Paths.get("target", "directory1", "rell")
        blockchain0ConfigGtv = run(runXmlFile(), resourceDirectory.toFile())
        doAndBuildBlocks(provConfig, provExecutor.getPostchainClient().transactionBuilder().initOperation(node0Host, node0Port))
    }

    @Test
    fun testProposeEnableDisableProvider() {
        //Then proposes a second provider to system cluster. Includes also add it to system voter_set.
        addSystemProv2()
        assertProviderEnabled(prov2Config.pubkey())

        // The new provider adds node 1 to system cluster. It becomes automatically signer of bcs in cluster. TODO: Start as
        //  replica and once it is in sync make it signer, (to not cause a potential blockbuilding stop.)
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, systemClusterName)

        //First provider proposes Disable prov2. Prov2 agrees:
        doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderAsync(prov2Config.pubkey()))
        val id = assertProposalTypeAndGetRowid(ProposalType.provider_state).id
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))
        assertProviderDisabled(prov2Config.pubkey())
    }

    @Test
    fun testTransferActionPoints() {
        //Then proposes a second provider to system cluster. Includes also add it to system voter_set.
        addSystemProv2()
        val myAP = provExecutor.listProvidersActionPoints(provConfig.pubkey())
        val othersAP = provExecutor.listProvidersActionPoints(prov2Config.pubkey())
        val amount: Long = 20
        doAndBuildBlocks(provConfig, provExecutor.transferActionPointsAsync(prov2Config.pubkey(), amount))
        val othersAPAfter = provExecutor.listProvidersActionPoints(prov2Config.pubkey())
        assertEquals(othersAP + amount, othersAPAfter)
        val myAPAfter = provExecutor.listProvidersActionPoints(provConfig.pubkey())
        assertEquals(myAP - amount - 1, myAPAfter)
    }

    /**
     * Add provider prov2 as system provider. Includes proposeEnable and promoting to system: active = true, system = true
     */
    private fun addSystemProv2() {
        doAndBuildBlocks(provConfig, provExecutor.registerProviderAsync(prov2Config.pubkey(), 1L))
        doAndBuildBlocks(provConfig, provExecutor.addProviderToClusterAsync(prov2Config.pubkey(), systemClusterName))
        doAndBuildBlocks(provConfig, provExecutor.proposeEnableProviderAsync(prov2Config.pubkey()))
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemAsync(prov2Config.pubkey(), true))
        assertProviderData(prov2Config.pubkey(), "", true)
    }

    /**
     * Two active providers. Prov2 proposes demotion of Prov1. Prov1 votes no.
     */
    @Test
    fun testProposeDegradeProviderVoteNo() {
        addSystemProv2()
        // Prov2 proposes degradation/demotion of prov1.
        doAndBuildBlocks(provConfig, prov2Executor.proposeProviderIsSystemAsync(provConfig.pubkey(), false))

        // Prov votes no
        voteNo(ProposalType.provider_is_system)
        val listVoterSet = provExecutor.listVoterSetMembers(voterSetSystemP)
        assertEquals(2, listVoterSet.size)
    }

    /**
     * New voter set with two providers and SYSTEM_P as governor. Create and update members. Change governor.
     */
    @Test
    fun testVoterSet() {
        addSystemProv2()

        val voterSetName = "Ellen"
        val providersList = "${provConfig.pubkey()},${prov2Config.pubkey()}"
        doAndBuildBlocks(
                provConfig, provExecutor.createVoterSetAsync(
                voterSetName, providersList, 0,
                voterSetSystemP
        )
        )
        assertAdded("get_voter_set", "name", GtvString(voterSetName))
        val listVotersets = provExecutor.listVoterSets()
        assertEquals(voterSetName, listVotersets[2].name)
        assertEquals(voterSetSystemP, provExecutor.getVoterSetGovernor(voterSetName))

        var members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey(), prov2Config.pubkey()), members.map { it.toHex() })


        //remove prov2 from Ellen. Note that with two providers in governance set, both must be OK with the member update.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetMemberAsync(voterSetName, prov2Config.pubkey(), false))
        var id = assertProposalTypeAndGetRowid(ProposalType.voter_set_update).id
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))
        members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey()), members.map { it.toHex() })

        //Make Ellen her own governor.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetGovernorAsync(voterSetName, voterSetName))
        id = assertProposalTypeAndGetRowid(ProposalType.voter_set_update).id
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))

        //add prov2 to voter set Ellen again. Since now only one member, no voting is needed for this proposal to be applied.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetMemberAsync(voterSetName, prov2Config.pubkey(), true))
        members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey(), prov2Config.pubkey()), members.map { it.toHex() })
    }

    @Test
    fun testProposeContainerAndLimits() {
        //add new container to system cluster
        val containerName = "container1"
        doAndBuildBlocks(
                provConfig,
                provExecutor.createContainerAsync(containerName, systemClusterName, voterSetSystemP)
        )
        assertAdded("get_container", "name", GtvString(containerName))

        val default = provExecutor.listContainerLimits(containerName)
        assertEquals(3, default.size)

        //Now updated container resource limits and check result
        proposeAndAssertContainerLimits(
                containerName,
                mapOf("ramm" to 123L),
                mapOf("ram" to -1L, "cpu" to -1L, "storage" to -1L)
        )

        proposeAndAssertContainerLimits(
                containerName,
                mapOf("ram" to 123L),
                mapOf("ram" to 123L, "cpu" to -1L, "storage" to -1L)
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
        val updated = provExecutor.listContainerLimits(containerName)
        assertEquals(expected, updated)
    }

    @Test
    fun testProposeClusterLimits() {
        val clusterName = "Vera"
        val providersList = provConfig.pubkey()
        // create cluster, initial providers added
        doAndBuildBlocks(
                provConfig,
                provExecutor.createClusterAsync(
                        clusterName, providersList, voterSetSystemP, voterSetSystemP
                )
        )

        var limits = mapOf("ramm" to 123L)
        var expected = mapOf("ram" to -1L, "cpu" to -1L, "storage" to -1L)
        proposeAndAssertClusterLimits(clusterName, limits, expected)

        limits = mapOf("ram" to 123L)
        expected = mapOf("ram" to 123L, "cpu" to -1L, "storage" to -1L)
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
        val updated = provExecutor.listClusterLimits(clusterName)
        assertEquals(expected, updated)
    }

    @Test
    fun testGetContainers() {
        // add new container to system cluster
        val containerName = "container1"
        doAndBuildBlocks(
                provConfig,
                provExecutor.createContainerAsync(containerName, systemClusterName, voterSetSystemP)
        )

        val expected = arrayOf("container1", "system")

        fun List<Gtv>.names() = map { it.asDict()["name"]?.asString() }
                .sortedWith(naturalOrder<String>())
                .toTypedArray()

        // asserting all containers
        val all = prov2Executor.listContainers().map { it.name }.toTypedArray()
        assertContentEquals(expected, all)

        // asserting cluster containers
        val clusterContainers = prov2Executor.listClusterContainers("system").map { it.name }.toTypedArray()
        assertContentEquals(expected, clusterContainers)

        // asserting UNKNOWN cluster containers
        val unknownClusterContainers = prov2Executor.listClusterContainers("unknown").map { it.name }.toTypedArray()
        assertContentEquals(arrayOf(), unknownClusterContainers)

        // asserting node containers
        val nodeContainers = prov2Executor.listContainersForNode(nodes[0].pubKey).map { it.name }.toTypedArray()
        assertContentEquals(expected, nodeContainers)

        // asserting UNKNOWN node containers
        val unknownKey = KeyPairHelper.pubKeyHex(77) // node 77
        assertThrows<UserMistake> {
            prov2Executor.listContainersForNode(unknownKey)

        }
    }

    @Test
    fun testGetBlockchainsForContainer() {
        val bcs = prov2Executor.listBlockchainsForContainer(systemContainerName)
        assertEquals(1, bcs.size)
        assertEquals(nodes[0].getBlockchainRid(0)!!.toHex(), bcs[0].toHex())
    }

    @Test
    fun testGetContainerForBlockchain() {
        val chain0Brid = nodes[0].getBlockchainRid(0)!!
        val actualContainer = prov2Executor.getContainerForBlockchain(chain0Brid.toHex())

        assertEquals(systemContainerName, actualContainer)
    }

    @Test
    fun testGetContainerForUnknownBlockchain() {
        assertThrows<UserMistake> {
            prov2Executor.getContainerForBlockchain(BlockchainRid.ZERO_RID.toHex())
        }
    }

    @Test
    fun testProposeAddBlockchainXmlWithDependency() {
        //add new container to system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(container1, systemClusterName, voterSetSystemP))
        //propose new bc in new container:
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1, "1"))
        assertEquals(2, provExecutor.listBlockchains(false).size)

        //test building blocks for new bc
        buildBlock(100, 4)

        //add yet another bc, dependent on previous one
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlDependencyFile, "xml", container1, "2"))
        val listOfBcs = provExecutor.listBlockchains(false)
        val listOfDependencies = provExecutor.listBlockchainDependencies(listOfBcs[2].toHex(), 0)

        assertEquals(1, listOfDependencies.size)
        assertEquals(listOfBcs[1].toHex(), listOfDependencies[0].first.toHex())
        assertEquals(container1, listOfDependencies[0].second)

        //Now make sure that you cannot delete a bc that someone else is dependent on
        proposeBlockchainAction(provClient, listOfBcs[1], BlockchainAction.remove)
        assertEquals(3, provExecutor.listBlockchains(false).size)
    }

    /**
     * Includes creating, listing of clusters as well as updating the cluster providers. And updating deployer.
     */
    @Test
    fun testCluster() {
        val newClusterName = "Vera"
        val providersList = provConfig.pubkey()
        //create cluster, initial providers added
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                newClusterName, providersList,
                voterSetSystemP, voterSetSystemP
        )
        )
        assertAdded("get_cluster", "name", GtvString(newClusterName))

        var clusters = provExecutor.listClustersForProvider(provConfig.pubkey())
        assertEquals(listOf(systemClusterName, newClusterName), clusters)

        doAndBuildBlocks(provConfig, provExecutor.registerProviderAsync(prov2Config.pubkey(), 0))
        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeClusterProviderAsync(newClusterName, prov2Config.pubkey(), add = true)
        )
        clusters = provExecutor.listClustersForProvider(prov2Config.pubkey())
        assertEquals(listOf(newClusterName), clusters)

        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeClusterProviderAsync(newClusterName, prov2Config.pubkey(), add = false)
        )
        clusters = provExecutor.listClustersForProvider(prov2Config.pubkey())
        assertEquals(listOf(), clusters)

        // change deployer
        doAndBuildBlocks(provConfig, provExecutor.proposeClusterDeployerAsync(newClusterName, voterSetSystem))
        val clusterInfo = provExecutor.getClusterInfo(newClusterName)
        assert(clusterInfo.deployer).isEqualTo(voterSetSystem)

        // cluster providers
        val clusterProviders = provExecutor.getClusterProviders(newClusterName)
        println(clusterProviders.toTypedArray().contentToString())
        assert(clusterProviders.size).isEqualTo(1)
        assert(clusterProviders.first().pubkey.hex()).isEqualTo(provConfig.pubkey())
        // UNKNOWN cluster providers
        assertThrows<UserMistake> {
            provExecutor.getClusterProviders("unknown cluster name")
        }
    }

    @Test
    fun testProposeConfiguration() {
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.blockchainRid.toHex(), bcConfig1xmlFile,
                20L, "xml", false
        )
        )
        assertNextConfiguration(provConfig, 20L)
    }

    private fun voteNo(proposalType: ProposalType) {
        val id = assertProposalTypeAndGetRowid(proposalType).id
        doAndBuildBlocks(provConfig, provExecutor.voteAsync(id, false))
    }

    @Test
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        //includes prov adds prov2 to system cluster
        addSystemProv2()

        // Add node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)

        // Get next configuration height after adding new node as blockchain's signer
        // expected next configuration height = -1 + init + addNode + proposeEnableProv + proposeBlockhain + addprov2toCluster + addNode + 5 = 10 (with vote included in proposal)
        assertNextConfiguration(provConfig, 10L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(2)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(Duration.ONE_SECOND).until {
                doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderAsync(prov2Config.pubkey()))
                true
            }
        }
    }

    @Test
    fun testRemoveBlockchainSigners() {
        addSystemProv2()
        // Prov2 adds node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)

        doAndBuildBlocks(prov2Config, provExecutor.removeNodeAsync(node1Pubkey))
        val listBlockchainSigners = provExecutor.listBlockchainSigners(provConfig.blockchainRid.toHex())
        assertEquals(1, listBlockchainSigners.size)
    }

    @Test
    fun testPauseBlockchain() {
        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1, "1"))

        //pause new bc
        var bcs = provExecutor.listBlockchains(false)
        val bridToPause = bcs[1]
        proposeBlockchainAction(provClient, bridToPause, BlockchainAction.pause)

        bcs = provExecutor.listBlockchains(true)
        assertEquals(2, bcs.size)
        bcs = provExecutor.listBlockchains(false)
        assertEquals(1, bcs.size)

        // try building blocks of pause bc
        assertBuildBlockFailure()

        proposeBlockchainAction(provClient, bridToPause, BlockchainAction.resume)
        bcs = provExecutor.listBlockchains(false)
        assertEquals(2, bcs.size)

        // build after unpause
        buildBlock(100, 3)
    }

    @Test
    fun testDeleteBlockchain() {
        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1, "1"))

        var bcs = provExecutor.listBlockchains(false)
        assertEquals(2, bcs.size)

        // delete new bc
        proposeBlockchainAction(provClient, bcs[1], BlockchainAction.remove)

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
        var listBlockchains = provExecutor.listBlockchainsForNode(node1Pubkey)
        assertEquals(1, listBlockchains.size) // Always know about itself

        listBlockchains = provExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)
    }


    @Test
    fun testGetBlockchainLastHeight() {
        val h = provExecutor.getBlockchainLastHeight(provConfig.blockchainRid.toHex())
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 + vote = 3
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 = 2 (vote included in proposal)
        assertEquals(0, h)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        val blockchain = provExecutor.getBlockchainConfiguration(provConfig.blockchainRid.toHex(), 0L)
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
        val listBlockchains = provExecutor.listBlockchains(false)
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testAddBlockchainReplicas() {
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addBlockchainReplicaAsync(provConfig.blockchainRid.toHex(), node1Pubkey), 1)
        assertBlockchainReplica(provConfig, node1Pubkey, node1Host, node1Port)

        val listBlockchainReplicas = provExecutor.listBlockchainReplicas(provConfig.blockchainRid.toHex())
        assertEquals(1, listBlockchainReplicas.size)
    }

    @Test
    fun testListBlockchainSigners() {
        //Add second system provider
        addSystemProv2()

        //prov adds prov2 to system cluster
        doAndBuildBlocks(provConfig, provExecutor.addProviderToClusterAsync(prov2Config.pubkey(), "system"))

        // Prov2 adds new node to system cluster => Two blockchain signers in system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, "system")

        val listBlockchainSigners = provExecutor.listBlockchainSigners(provConfig.blockchainRid.toHex())
        assertEquals(2, listBlockchainSigners.size)
    }

    /**
     * Includes also test of listClusters
     */
    @Test
    fun testListContainerReplicas() {
        //create new cluster Vera.
        val clusterA = "A"
        val clusterB = "B"
        val containerName = "C"
        val providersList = provConfig.pubkey()
        //create two new clusters with initial provider added
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                clusterA, providersList,
                voterSetSystemP, voterSetSystemP
        )
        )
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                clusterB, providersList,
                voterSetSystemP, voterSetSystemP
        )
        )

        val clusterList = provExecutor.listClusters()
        assertEquals(listOf("system", clusterA, clusterB), clusterList)

        //add a container C to cluster A
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(containerName, clusterA, voterSetSystemP))
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
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemAsync(prov2Config.pubkey(), false))

        val type = ProposalType.provider_is_system
        val id = assertProposalTypeAndGetRowid(type)
        val proposal = provExecutor.getPostchainClient().getProposal(id)!!
        val actualType = proposal.type
        val propid = proposal.id
        val timestamp = proposal.timestamp
        val proposedBy = proposal.proposedBy.toHex()

        assertEquals(provConfig.pubkey(), proposedBy, "wrong proposed_by")
        assertEquals(id, propid, "wrong idx")
        assertEquals(ProposalType.provider_is_system, actualType, "Wrong proposal type")
        assertNotEquals(0, timestamp, "timestamp is 0")

    }

    //    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: ProposalType): RowId {
        val proposals = provExecutor.listProposalsSince(0)
        val type = (proposals[0].proposalType.name)
        assertEquals(expectedType.toString(), type, "Wrong proposal type")
        return (proposals[0].rowid)
    }

    private fun proposeBlockchainAction(provClient: PostchainClient, brid: ByteArray, action: BlockchainAction) {
        provClient.transactionBuilder().proposeBlockchainActionOperation(
                provClient.config.signers.first().pubKey.wData, BlockchainRid(brid), action
        ).also {
            doAndBuildBlocks(provClient.config, it)
        }
    }
}