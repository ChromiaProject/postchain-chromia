# README #

Postchain MC is used to issue commands to an entire group of Chromia/Postchain nodes.

### What is Postchain MC for? ###

* "Postchain-MC" (=this project) is a command line tool which submits transactions to 
the Chromia0 directory chain. 

* "chromia0" defines code for the 'directory' blockchain which manages a group of nodes. 
Using a 'directory' chain is convenient because it will help to synchronize 
configuration changes between nodes (where manual synchronization would be time consuming).

* Key concepts are:
  - "nodes" (machines running Chromia Dapps),
  - "providers" (private persons/organizations responsible for the "nodes")
  - "blockchains" (data/Dapps hosted by the "nodes")
  - "signers" (nodes responsible for verifying and creating data blocks)
  - "replicas" (nodes who can read the blockchains but not create blocks)
  - "blockchain configuration" (is certain critical settings for the blockchain, these might
   change over time, and it is Chromia0's responsibility to keep these changes is synch between nodes)

* Example: One common situation is when a provider wants to add its node to a 
group of providers (and will then host the same blockchains andn Dapps as the other
 nodes in this group). We do this using the "./pmc-c0.sh add-node" command.

### How do I get set up? ###

* Summary of set up
* Configuration
* Dependencies
* Database configuration
* How to run tests
* Deployment instructions

### Contribution guidelines ###

Unit tests run chromia0 nodes internally, but you can also run them manually

