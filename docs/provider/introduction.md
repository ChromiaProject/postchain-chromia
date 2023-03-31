# Introduction

Welcome to the provider portal. On this site you will find everything you need as a provider, from starting a node 
to how to interact with the network.

## Provider Types

There are three roles on the network

| Provider role                                 | Permitted actions                                           |
|-----------------------------------------------|-------------------------------------------------------------|
| System Provider (SP)                          | Governance of the system chain                              |
| Node Provider (NP)                            | Add nodes to dapp clusters                                  |
| Community Node Provider / Dapp Provider (CNP) | Add community(replica) nodes and deploy dapps to containers |


## Glossary

| Term                                 | Description                                                                                                                                                                                                                                                                                                                                                        |
|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dapp                                 | A distributed application where the back-end running on nodes and the front end on some client software.                                                                                                                                                                                                                                                           |
| Management chain (bc0)               | Dapp for managing settings/configurations for all blockchains in the network. This is convenient because it will help to synchronize configuration changes between nodes (where manual synchronization would be time consuming). `bc0` is managing all blockchains in the network, including itself. Comes in a few flavors; Chromia0, Enterprise0 and Directory1. |
| Postchain MC (PMC)                   | A command line tool which submits transactions to the management chain chain.                                                                                                                                                                                                                                                                                      |
| Node                                 | Machine running bc0 dapp.                                                                                                                                                                                                                                                                                                                                          |
| Providers                            | Private persons or organizations responsible for the nodes.                                                                                                                                                                                                                                                                                                        |
| Blockchain                           | The data the "dapps" use are stored in "blockchains" that are hosted by the "nodes". Example: The  dapp 'HorseDapp' use the blockchain 'HorseBC' to store its data, but also reads from the blockchain 'AllSpeciesRepo' that is managed by a different dapp.                                                                                                       |
| Block                                | The blocks hold the actual data of the blockchain. Each block depend on the previous one and thereby create a chain of blocks.                                                                                                                                                                                                                                     |
| Blockchain configuration (bc-config) | The blockchain configuration fully defines the blockchain's behavior. Both signer nodes and replica nodes need to know the configuration to create and verify blocks.                                                                                                                                                                                              |
| Signer                               | Node responsible for verifying and creating data blocks.                                                                                                                                                                                                                                                                                                           |
| Replica                              | Node that verifies data blocks, but do not create blocks.                                                                                                                                                                                                                                                                                                          |
| Blockchain RID (brid)                | Global reference ID for a blockchain, common for all nodes.                                                                                                                                                                                                                                                                                                        |
| Chain ID                             | Local blockchain ID. Can be different on different nodes.                                                                                                                                                                                                                                                                                                          |


## Node Management API

When postchain is run in managed mode (as it always does with a management chain), it requires that the module in 
chain 0, bc0, fulfills a certain API. This API is responsible for providing postchain with information about which 
blockchains to run, blockchain configurations and node configurations.

Any postchain module that fulfills this API may serve as bc0. It is totally up to the module to decide how to answer 
these queries. The module is typically written in Rell where blockchains and nodes are managed through consensus 
voting, for example >50% of bc0 signers must agree on a new blockchain before it gets visible through nm_api. 
Another valid approach might be to have a designated admin that adds blockchains and configurations, but that comes 
with some centralization, of course.

This document tries to refrain from explaining individual chain0 modules. We focus instead on the general process of 
setting up a cluster in managed mode, which doesn't depend on which chain0 module is used.

Postchain in managed mode expects that bc0 provides the following queries:

|                                    |                                                                                                                                                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| nm_get_peer_infos                  | Return a list of peers (IP address, port, pubkey)                                                                                                                                                                       |
| nm_get_peer_list_version           | Return an integer version. This might for example be the timestamp of the last block a the time the node list was updated, but could be anything, as long as it changes every time the node list changes.               |
| nm_compute_blockchain_list         | Return a list of blockchain RIDs that the node identified by the provided pubkey should run.                                                                                                                            |
| nm_get_blockchain_configuration    | Return the effective configuration, as a byte array, of a blockchain with RID brid at the provided height.                                                                                                              |
| nm_find_next_configuration_height  | Return the height at which the next configuration change takes place. If no future configuration changes are planned, or if brid doesn't exist, null is returned. The returned integer is strictly greater than height. |
| nm_get_blockchain_replica_node_map | Returns all replicas for each blockchain, thus a map from brid to replica node's pubkey.                                                                                                                                |
