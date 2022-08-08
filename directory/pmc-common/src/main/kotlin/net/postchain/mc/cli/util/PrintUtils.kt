package net.postchain.mc.cli.util

import net.postchain.common.toHex
import net.postchain.gtv.Gtv

object PrintUtils {
    //    This function is for printing results from get_nodes_by_provider and get_nodes_with_provider. Query listNodes
//    (nm_get_peer_infos) returns an array, not a dict.
    fun printNodes(nodes: List<Gtv>, includeInactive: Boolean = true, showProvider: Boolean = true) {
        nodes.forEach {
            val n = it.asDict()
            val isActive = active(n)
            if (isActive || includeInactive) {
                println("host: ${n["host"]!!.asString()}")
                println("port: ${n["port"]!!.asInteger()}")
                println("pubkey: ${n["pubkey"]!!.asByteArray().toHex()}")
                println("node active: ${isActive}")
                println("last_updated: ${n["last_updated"]!!.asInteger()}")
                if (showProvider) {
                    println("provider pubkey: ${n["provider"]!!.asByteArray().toHex()}")
                    println("provider name: ${n["name"]!!.asString()}")
                    println("provider active: ${n["provider_active"]!!.asBoolean()}")
                    if (n.containsKey("beneficiary")) { //Enterprise0 does not have this key.
                        println("provider beneficiary: ${n["beneficiary"]!!.asByteArray().toHex()}")
                    }
                }
                println("")
            }
        }
    }

    //Used by listBlockchainReplicas
    fun printBlockchainReplicas(list: List<Gtv>, includeInactive: Boolean = true) {
        list.forEach { item ->
            val isActive = item.get(4).asBoolean()
            if (isActive || includeInactive) {
                println("BlockchainRID: ${item.get(0).asByteArray().toHex()}")
                println("Node pubkey: ${item.get(1).asByteArray().toHex()}")
                println("Node host: ${item.get(2).asString()}")
                println("Node port: ${item.get(3).asInteger()}")
                println("Node active: ${isActive}")
                println("Node last_update: ${item.get(5).asInteger()}")
                println("")
            }
        }
    }

    //Used by listBlockchainSigners
    fun printBlockchainSigners(list: List<Gtv>, includeInactive: Boolean = true) {
        list.forEach { item ->
            val isActive = item.get(3).asBoolean()
            if (isActive || includeInactive) {
                println("Node pubkey: ${item.get(0).asByteArray().toHex()}")
                println("Node host: ${item.get(1).asString()}")
                println("Node port: ${item.get(2).asInteger()}")
                println("Node active: ${isActive}")
                println("Node last_update: ${item.get(4).asInteger()}")
                println("")
            }
        }
    }

    fun printProviders(providers: List<Gtv>, includeInactive: Boolean = true) {
        providers.forEach {
            val dict = it.asDict()
            val isActive = dict["active"]!!.asBoolean()
            if (isActive || includeInactive) {
                println("pubkey: ${dict["pubkey"]!!.asByteArray().toHex()}")
                println("name: ${dict["name"]!!.asString()}")
                println("active: ${isActive}")
                println("is system provider: ${dict["system"]!!.asBoolean()}")
                println("tier: ${dict["tier"]!!.asInteger()}")
                if (dict.containsKey("beneficiary")) { //Enterprise0 does not have this key.
                    println("beneficiary: ${dict["beneficiary"]!!.asByteArray().toHex()}")
                }
                println("")
            }
        }
    }

    fun printProvidersNamePubKey(providers: List<Gtv>): String {
        val template = "%-15s%-15s"
        val header = template.format("PROVIDER", "PUBKEY")

        return providers.joinToString("\n", prefix = "$header\n") {
            val p = it.asDict()
            val name = p["name"]?.asString()
            val pubkey = p["pubkey"]?.asByteArray()?.toHex()
            template.format(name, pubkey)
        }
    }

    /**
     * TODO: Inactivation of clusters is not yet implemented
     */
    fun printClusters(clusters: List<Gtv>, includeInactive: Boolean = false) {
        clusters.forEach {
            val dict = it.asDict()
//            val isActive = dict["active"]!!.asBoolean()
            val isActive = true
            if (isActive || includeInactive) {
                println("name: ${dict["name"]!!.asString()}")
                println("deployer: ${dict["deployer"]!!.asString()}")
                println("governor: ${dict["governor"]!!.asString()}")
                println("is operational: ${dict["is_operational"]!!.asBoolean()}")
                println("")
            }
        }
    }

    //get_nodes_by_provider has key "active", get_nodes_with_provider has key "node_active"
    private fun active(n: Map<String, Gtv>): Boolean {
        if (n.containsKey("node_active")) {
            return n["node_active"]!!.asBoolean()
        }
        if (n.containsKey("active")) {
            return n["active"]!!.asBoolean()
        }
        return false
    }

}
