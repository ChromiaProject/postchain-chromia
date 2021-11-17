package net.postchain.mc.mcu

import com.google.gson.GsonBuilder

object Json {

    val gson = GsonBuilder().setPrettyPrinting().create()!!

}