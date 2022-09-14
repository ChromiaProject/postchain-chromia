package net.postchain.mc.cli.util

import net.postchain.common.toHex
import net.postchain.gtv.Gtv

object ProvidersPrinter {

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

}