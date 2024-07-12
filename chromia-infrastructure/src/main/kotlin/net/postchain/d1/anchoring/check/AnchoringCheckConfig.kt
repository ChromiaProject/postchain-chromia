package net.postchain.d1.anchoring.check

import net.postchain.config.app.AppConfig

data class AnchoringCheckConfig(
        val clusterAnchorCheckIntervalMs: Long,
        val systemAnchorCheckIntervalMs: Long,
        val evmAnchorCheckIntervalMs: Long,
        val rpcUrls: List<String>,
        val anchoringContractAddress: String,
) {
    companion object {
        private const val ANCHORING_CHECK_PREFIX = "ANCHORING_CHECK_"
        private const val CLUSTER_ANCHOR_CHECK_INTERVAL_MS = "${ANCHORING_CHECK_PREFIX}CLUSTER_ANCHOR_CHECK_INTERVAL_MS"
        private const val SYSTEM_ANCHOR_CHECK_INTERVAL_MS = "${ANCHORING_CHECK_PREFIX}SYSTEM_ANCHOR_CHECK_INTERVAL_MS"
        private const val EVM_ANCHOR_CHECK_INTERVAL_MS = "${ANCHORING_CHECK_PREFIX}EVM_ANCHOR_CHECK_INTERVAL_MS"
        private const val RPC_URLS = "${ANCHORING_CHECK_PREFIX}RPC_URLS"
        private const val ANCHORING_CONTRACT_ADDRESS = "${ANCHORING_CHECK_PREFIX}ANCHORING_CONTRACT_ADDRESS"

        @JvmStatic
        fun fromAppConfig(config: AppConfig): AnchoringCheckConfig {
            return AnchoringCheckConfig(
                    config.getEnvOrLong(CLUSTER_ANCHOR_CHECK_INTERVAL_MS, "anchoring-check.cluster-anchor-check-interval-ms", 3600000),
                    config.getEnvOrLong(SYSTEM_ANCHOR_CHECK_INTERVAL_MS, "anchoring-check.system-anchor-check-interval-ms", 3600000),
                    config.getEnvOrLong(EVM_ANCHOR_CHECK_INTERVAL_MS, "anchoring-check.evm-anchor-check-interval-ms", -1),
                    config.getEnvOrListProperty(RPC_URLS, "anchoring-check.rpc-urls", listOf()),
                    config.getEnvOrString(ANCHORING_CONTRACT_ADDRESS, "anchoring-check.anchoring-contract-address", ""),
            )
        }
    }
}
