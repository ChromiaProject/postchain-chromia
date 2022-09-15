package net.postchain.mc.cli.votingupdates

fun formatThreshold(threshold: Long): String {
    return when (threshold) {
        0L -> "Super mayority (67%)"
        -1L -> "Majority (50%)"
        else -> threshold.toString()
    }
}
