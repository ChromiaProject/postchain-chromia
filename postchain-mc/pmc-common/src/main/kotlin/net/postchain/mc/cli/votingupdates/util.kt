package net.postchain.mc.cli.votingupdates

fun formatThreshold(threshold: Long): String {
    return when (threshold) {
        0L -> "super majority (>66.66%)"
        -1L -> "majority (>50%)"
        else -> threshold.toString()
    }
}
