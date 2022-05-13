=============
Introduction
=============

We start with a few concepts that are useful to know:

Dapp
   A distributed application where the back-end running on nodes and the front end on some client software.

Management chain (bc0)
    Dapp for managing settings/configurations for all blockchains in the network. This is convenient because it will help to synchronize configuration changes between nodes (where manual synchronization would be time consuming). `bc0` is managing all blockchains in the network, including itself.
    Comes in a few flavors; Chromia0, Enterprise0 and Directory1.

Postchain-MC
   A command line tool which submits transactions to the management chain chain.

Node
   Machine running bc0 dapp.

Providers
   Private persons or organizations responsible for the nodes.

Blockchain
   The data the "dapps" use are stored in "blockchains" that are
     hosted by the "nodes".

   Example: The  dapp 'HorseDapp' use the blockchain 'HorseBC' to store its data,
     but also reads from the blockchain 'AllSpeciesRepo' that is managed by a different dapp.

Blockchain block
   The blocks hold the actual data of the blockchain. Each block depend on the previous one and thereby create a chain of blocks.

Blockchain configuration
   The blockchain configuration fully defines the blockchain's behavior. Both signer nodes and replica nodes need to know the configuration to create and verify blocks.

Signer
   Node responsible for verifying and creating data blocks.

Replica
   Node that verifies data blocks, but do not create blocks.

Blockchain RID (brid)
   Global reference ID for a blockchain, common for all nodes.

Chain ID
   Local blockchain ID. Can be different on different nodes.


One common situation is when a provider wants to add a node to the set of allowed nodes on the network
(after this action, the node will be available for running dapps and blockchains).
We do this using the ``./pmc-c0.sh add-node`` command.


Node manager API
================

When postchain is run in managed mode (as it always does with a management chain), it requires that the module in chain 0, bc0, fulfills a certain API. This API is responsible for providing postchain with information about which blockchains to run, blockchain configurations and node configurations.

Any postchain module that fulfills this API may serve as bc0. It is totally up to the module to decide how to answer these queries. The module is typically written in Rell where blockchains and nodes are managed through consensus voting, for example >50% of bc0 signers must agree on a new blockchain before it gets visible through nm_api. Another valid approach might be to have a designated admin that adds blockchains and configurations, but that comes with some centralization, of course.

This document tries to refrain from explaining individual chain0 modules. We focus instead on the general process of setting up a cluster in managed mode, which doesn't depend on which chain0 module is used.

Postchain in managed mode expects that bc0 provides the following queries:

nm_get_peer_infos()
  Return a list of peers (IP address, port, pubkey)

nm_get_peer_list_version()
  Return an integer version. This might for example be the timestamp of the last block a the time the node list whas updated, but could be anything, as long as it changes every time the node list changes.

nm_compute_blockchain_list(pubkey)
  Return a list of blockchain RIDs that the node identified by the provided pubkey should run.

nm_get_blockchain_configuration(brid, height)
  Return the effective configuration, as a byte array, of a blockchain with RID brid at the provided height.

nm_find_next_configuration_height(brid, height)
  Return the height at which the next configuration change takes place. If no future configuration changes are planned, or if brid doesn't exist, null is returned. The returned integer is strictly greater than height.

nm_get_blockchain_replica_node_map
  Returns all replicas for each blockchain, thus a map from brid to replica node's pubkey.

nm_get_node_replica_map
  For making a node a full clone of another node.

GTXManagedNodeDataSource is the class responsible for fetching blockchain configurations and node configurations from the database.


Bootstrapping
===============

Bootstrapping is the process of setting up a mangaged mode system from scratch. It consists of the following activities:

* Prepare the host machine
* Install the postchain software
* Configure blockchain 0

If you want to set up your own blockchain cluster, first make sure you have the following software:

* Management Client (pmc) postchain-mc-1.0-SNAPSHOT-dist.tar.gz
* Node software (postchain-node) rellr-0.10.3-dist.tar.gz with a recent (>=27547d61caaf7d5e84cf1b2102a79de9bf6b872b) build of postchain-3.3.1-SNAPSHOT
* PostgreSQL

Some inspiration can be found in `Chromia0 setup provider procedure <https://docs.google.com/document/d/1Lho7HxI9hHisrhk9A_2Sit3f4nO1ZJRjkMl9L7Qjnf0/edit?usp=sharing>`_.


Setup a node
===============

To setup a node in managed mode, the following steps are required:

* Install Postchain and Rell
* Create a `.properties` configuration file to setup database access and a few other things.
* Get hold of a blockchain configuration for chain 0:

  - If first node in system
    - Create a blockchain configuration for blockchain 0 (If first node)
  - else
    - Get the blockchain configuration from someone who has it
* Add blockchain 0 to the system (postchain.sh add-blockchain ...)
* Start the node
* If first node in system
   - Make blockchain 0 manageable (pmc.sh add-blockchain)

Node config file
-------------------

A node must have a `.properties` file. Here is an example of such a file.::


   ################################################

   # Here we use managed mode so we read host, port, pubkey from database. See table c0.node.
   configuration.provider.node=managed

   # The InfrastructureFactory creates BlockchainConfigurationProvider, BlockchainInfrastructure, and BlockchainProcessManager
   # BlockchainConfigurationProvider provides blockchain configurations for chainIds.
   # BlockchainInfrastructure
   #   * Decodes blockchain configurations from the BlockchainConfigurationProvider
   #   * Creates a BlockchainEngine that knows how to build blocks
   #   * Creates a BlockchainProcess that knows how to use a BlockchainEngine and syncronize with other nodes.
   # BlockchainProcessManager knows how to start and stop blockchains, and can decide when it's time to restart a
   # blockchain, typically due to configuration changes.
   infrastructure=net.postchain.managed.Chromia0InfrastructureFactory

   database.driverclass=org.postgresql.Driver
   database.url=jdbc:postgresql://localhost:5432/postchain
   database.username=postchain
   database.password=postchain
   database.schema=managedtest
   api.port=7740
   api.basepath=
   messaging.privkey=3132333435363738393031323334353637383930313233343536373839303131
   messaging.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

   ################################################
