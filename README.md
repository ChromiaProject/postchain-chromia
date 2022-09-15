# README #

Postchain MC is used to issue commands to an entire group of Chromia/Postchain nodes.

### What is Postchain MC for? ###

* "Postchain-MC" (=this project) is a command line tool which submits transactions to 
the Chromia0 directory chain. 

* "chromia0" defines code for the 'directory' blockchain which manages 
settings/configuration for the nodes. Using a 'directory' chain is convenient because 
it will help to synchronize configuration changes between nodes 
(where manual synchronization would be time consuming).

* Key concepts are:
  - "nodes" (machines running Chromia Dapps),
  - "providers" (private persons/organizations responsible for the "nodes")
  - "dapp" (a distiduted application where the back-end running on "nodes" and 
     the front end on some client software)
  - "blockchains" (the data the "dapps" use are stored in "blockchains" that are 
     hosted by the "nodes". 
     Example: The  dapp 'HorseDapp' use the blockchain 'HorseBC' to store its data, 
     but also reads from the blockchain 'AllSpeciesRepo' that is managed by a different dapp.)
  - "blockchain blocks" (the blocks hold the actual data of the blockchain)
  - "blockchain configuration" (the configuration fully defines the blockchain's behavior. 
     Both "signer" nodes and "replica" nodes need to know the configuration to create and verify blocks.)
  - "signers" (nodes responsible for verifying and creating data blocks)
  - "replicas" (nodes that verifies data blocks, but do not create blocks)

* Example: One common situation is when a provider wants to add one node 
to the set of allowed nodes on the Chromia network 
(after this action, the node will be available for running dapps and blockchains).
 We do this using the "./pmc-c0.sh add-node" command.

### How do I get set up? ###

* Summary of set up
* Configuration
* Dependencies
* Database configuration
* How to run tests
* Deployment instructions

### Examples ###
Сonfig file `config.properties`:
```properties
api-url=http://localhost:7740
blockchain-rid=71B35D0F8E7056E663FF003E9C64919393D67666866521805241B92F8C2DE1DD
privkey=<...>
pubkey=<...>
```

Prints top level commands:
```shell
 ./pmc.sh
```

Lists providers:
```shell
 ./pmc.sh provider list --config config.properties
```

### Contribution guidelines ###

Unit tests run chromia0 nodes internally, but you can also run them manually

