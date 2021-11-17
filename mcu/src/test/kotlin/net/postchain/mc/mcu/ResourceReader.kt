package net.postchain.mc.mcu

object ResourceReader {

    fun read(resourceName: String): String {
        return this.javaClass::class.java.getResource(resourceName).readText()
    }

}