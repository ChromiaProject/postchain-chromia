package net.postchain.mc.compatibility

import net.postchain.client.transaction.TransactionBuilder
import net.postchain.gtv.GtvFactory.gtv

object ApiCompatV2 {

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "model:cluster_resource_limit_type")
    enum class ClusterResourceLimitType {
        max_containers,
        default_container_max_blockchains,
        default_container_cpu,
        default_container_ram,
        default_container_storage,
        default_container_io_read,
        default_container_io_write
    }

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "proposal_cluster:propose_cluster_limits")
    fun TransactionBuilder.proposeClusterLimitsOperationV2(myPubkey: ByteArray,
                                                           clusterName: String,
                                                           limits: Map<ClusterResourceLimitType, Long>,
                                                           description: String) =
            addOperation("propose_cluster_limits", gtv(myPubkey),
                    gtv(clusterName),
                    gtv(limits.map { (k, v) -> gtv(gtv(k.ordinal.toLong()), gtv(v)) }),
                    gtv(description))


    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "model:container_resource_limit_type")
    enum class ContainerResourceLimitType {
        max_blockchains,
        cpu,
        ram,
        storage,
        io_read,
        io_write
    }

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "proposal_container.proposal_container_limits:propose_container_limits")
    fun TransactionBuilder.proposeContainerLimitsOperationV2(myPubkey: ByteArray,
                                                             containerName: String,
                                                             limits: Map<ContainerResourceLimitType, Long>,
                                                             description: String) =
            addOperation("propose_container_limits", gtv(myPubkey),
                    gtv(containerName),
                    gtv(limits.map { (k, v) -> gtv(gtv(k.ordinal.toLong()), gtv(v)) }),
                    gtv(description))

}