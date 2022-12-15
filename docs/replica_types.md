# Replica types:

~~* Node Replica: Node replicates node(s)
    - Doc: For making a node a full clone of another node.
    - D1: `query nm_get_node_replica_map(): list<list<byte_array>>`
      -- always empty
      -- TODO: change return type from [[key_peer_id, replica_peer_id_1, replica_peer_id_2, ...], ...] to map
    - Kotlin side: `fun getNodeReplicaMap(): Map<NodeRid, List<NodeRid>>`
      -- TODO: figure out how and where it is used~~
    - 2022.11.22: Removed: https://gitlab.com/chromaway/postchain/-/merge_requests/498

* Blockchain Replica: `entity blockchain_replica_node{ key blockchain, node; }`
    - D1: used in `query nm_compute_blockchain_list(node_id: pubkey): list<byte_array>`
      -- Doc: Return a list of blockchain RIDs that the node identified by the provided pubkey should run.
    - D1: used in `query nm_get_blockchain_replica_node_map(blockchain_rids: list<byte_array>): list<list<byte_array>>`
      -- Doc: Returns all replicas for each blockchain, thus a map from brid to replica node's pubkey.
    - Kotlin: `fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<NodeRid>>`
      -- TODO: figure out how and where it is used
    - Used in `query get_blockchain_replicas(blockchain)`, which is used in CLI::listBlockchainReplicas()
      -- operation add_bc_replica() / remove_bc_replica()
    - ~~Used in `function make_node_a_replica_in_cluster()`, which is not used [!]~~
    - Used in `_apply_delete_blockchain / _apply_resume_blockchain`, but commented in `_apply_pause_blockchain`
    - Used in `update_provider_state()` op
    - Not used in `remove_node()` op, but mentioned in TODO

* DB TABLE `blockchain_replicas`, configured locally via CLI

~~* Cluster container replica: `entity cluster_container_replica { key cluster, container; }`
    - Doc: Cluster replicate this container, but container belongs to another cluster that is responsible for block building.
    - My comment in MR:
      -- Perhaps, will be better to formulate "container1 replicates container2" regardless a cluster container1 belongs to.
      -- https://gitlab.com/chromaway/postchain-chromia/-/merge_requests/96#note_1126303348
    - Used in `operation add_container_replica(me: provider, cluster, container)`
    - Used in `operation remove_container_replica(me: provider, cluster, container)`
    - Used in `query get_container_replicas(container) -> cluster name`, which is not used [!]
    - Removed 2022.11.14~~

* Cluster replica node added:
    - See `entity cluster_replica_node {...}`
