package net.postchain.mc.cli.util

import net.postchain.chain0.model.ProviderTier

enum class ProviderType {
    COMMUNITY_NODE_PROVIDER,
    NODE_PROVIDER,
    SYSTEM_PROVIDER;

    fun toTier() = when (this) {
        COMMUNITY_NODE_PROVIDER -> ProviderTier.COMMUNITY_NODE_PROVIDER
        else -> ProviderTier.NODE_PROVIDER
    }

    fun shouldEnable(enable: Boolean) = enable && this == NODE_PROVIDER
}
