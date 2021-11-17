package net.postchain.mc.mcu.model

data class Dapp(
    val name: String,
    val brid: String,
    val config0Path: String,
    val active: Boolean,
    val cont: String, // container
    var lastConfigHeight: Long // init value: 10
) {

    fun resume(): Long {
        return ++lastConfigHeight
    }

    fun configure(): Long {
        return ++lastConfigHeight
    }

}
