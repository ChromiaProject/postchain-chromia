.. postchain-mc documentation master file, created by
   sphinx-quickstart on Thu Jan 28 11:08:12 2021.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

Welcome to postchain-mc's documentation!
========================================

The Enterprise0 is an enterprise level solution for organizations or companies that want to run their own blockchain network. 

Postchain MC is used to issue commands to an entire group of Chromia/Postchain nodes.

First a few concepts that are useful to know:

Postchain-MC
   A command line tool which submits transactions to the Chromia0 directory chain. 

Enterprise0 (bc0)
   Defines code for the 'mama' blockchain which manages settings/configuration for all blockchains in the network. Using a 'mama' chain is convenient because it will help to synchronize configuration changes between nodes (where manual synchronization would be time consuming). Mama is managing all blockchains in the network, including herself.

Nodes
   Machines running Chromia or Enterprise Dapps.

Providers
   Private persons or organizations responsible for the nodes.

Dapps
   A distiduted application where the back-end running on nodes and the front end on some client software).

Blockchains
   The data the "dapps" use are stored in "blockchains" that are 
     hosted by the "nodes". 
     
     Example: The  dapp 'HorseDapp' use the blockchain 'HorseBC' to store its data, 
     but also reads from the blockchain 'AllSpeciesRepo' that is managed by a different dapp.

Bblockchain blocks
   The blocks hold the actual data of the blockchain. Eack block depend on the previous one and thereby create a chain of blocks.

Blockchain configuration
   The blockchain configuration fully defines the blockchain's behavior. Both signer nodes and replica nodes need to know the configuration to create and verify blocks.

Signers
   Nodes responsible for verifying and creating data blocks.

Replicas
   Nodes that verifies data blocks, but do not create blocks.

Blockchain RID (brid)
   Global reference ID for a blockchain, common for all nodes.

Chain ID
   Local blockchain ID. Can be different on different nodes.


One common situation is when a provider wants to add one node 
to the set of allowed nodes on the Chromia network 
(after this action, the node will be available for running dapps and blockchains).
We do this using the ``./pmc-c0.sh add-node`` command.




The providers have the power
============================

In Enterprise0, it is the providers that together hold the power. With a voting system they agree on various configuration updates of the network. Providers propose updates. Before they can be applied, they need approval from a majority of the providers. Providers vote on the different proposals. There are six types of updates that need approval before they are applied.

=================    =======================================================
Proposal type        Description 
=================    =======================================================
conf                 New blockchain configuration for a specific height.
bc                   New blockchain, starting at height 0.
register_provider    New provider.
provider_state       Enable/disable a registered provider.
bc_signers           Update which nodes that should be signers for a bc
bc_stop              Stop building blocks for a given blockchain.
=================    =======================================================


Software
===============

If you want to set up your own blockchain cluster, first make sure you have the following software:

* Management Client (pmc) postchain-mc-1.0-SNAPSHOT-dist.tar.gz
* Node software (postchain-node) rellr-0.10.3-dist.tar.gz with a recent (>=27547d61caaf7d5e84cf1b2102a79de9bf6b872b) build of postchain-3.3.1-SNAPSHOT
* PostgreSQL

Some inspiration can be found in `Chromia0 setup provider procedure <https://docs.google.com/document/d/1Lho7HxI9hHisrhk9A_2Sit3f4nO1ZJRjkMl9L7Qjnf0/edit?usp=sharing>`_.


.. toctree::
   :hidden:
   :maxdepth: 2
   :caption: Contents:

   learn-by-example.rst
   queries.rst
   q-and-a.rst

   


Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
