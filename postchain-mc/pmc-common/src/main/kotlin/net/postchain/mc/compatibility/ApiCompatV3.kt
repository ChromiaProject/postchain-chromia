package net.postchain.mc.compatibility

import net.postchain.client.core.PostchainQuery
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject

object ApiCompatV3 {

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "common.queries:get_blockchain_info_list")
    fun PostchainQuery.getBlockchainInfoListV3(includeInactive: Boolean) =
            query("get_blockchain_info_list", GtvFactory.gtv(mapOf("include_inactive" to GtvFactory.gtv(includeInactive)))).asArray().map { v -> v.toObject<GetBlockchainInfoListResultV3>() }

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "")
    data class GetBlockchainInfoListResultV3(
            @Name("rid") val rid: WrappedByteArray,
            @Name("name") val name: String,
            @Name("active") val active: Boolean,
            @Name("container") val container: String,
            @Name("cluster") val cluster: String
    )

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "common.queries:get_container_blockchain")
    fun PostchainQuery.getContainerBlockchainV3(name: String) =
            query("get_container_blockchain", GtvFactory.gtv(mapOf("name" to GtvFactory.gtv(name)))).asArray().map { v -> v.toObject<GetContainerBlockchainResultV3>() }

    // @Generated("net.postchain.rell.codegen.CodeGenerator", comments = "")
    data class GetContainerBlockchainResultV3(
            @Name("rid") val rid: WrappedByteArray,
            @Name("name") val name: String,
            @Name("system") val system: Boolean,
            @Name("active") val active: Boolean
    )

}