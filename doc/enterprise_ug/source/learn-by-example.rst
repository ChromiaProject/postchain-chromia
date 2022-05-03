=============================================
Learn by an example: The AI project
=============================================

In this section you can learn how to set up your on blockchain network by studying an concrete example. The example project is called AI.

The cluster has four nodes. The first node Ai0 is the initial provider node. Bc0 is set up on this first node and also added by the Management client (postchain-mc) so that bc0 will become aware of itself. Then we can in a convenient  way update configuration, add a second blockchain to be managed by bc0: bcai. Also more nodes can be added and be made signers.

There are quite a few steps here, we’ll take them one at a time.


Initial Provider
================
The initial provider that can vote “yes” to the first blockchain, bc0, and is a module argument in the rell module, in run0.xml. Generate your provider keypair with:

Generate keypair for provider management console::

    ./pmc.sh keygen --save prov.cfg



Update your run-e0.xml accordingly. We used this pubkey of initial provider::
    03A692CDB2FD63037D883804A2028C5FBFD5E8A7E20E08BC387071220F8AD06710

Generate your blockchain configuration
=============================================
The blockchain configuration is generated with the script ` ./multigen.sh`. The configuration is generated from

#. a run.xml-file (in this example run0.xml)
#1. the rell code.

If parameter -d is not given, it will look for the rell code in the current directory.
Rell code for Enterprise0 is here:   ``/postchain-mc/src/main/rell/enterprise0/``
The rell code for Chromia0 is here: ``/postchain-mc/src/main/rell/chroma0/``::

    ./multigen.sh -d  ../../../postchain-mc/src/main/rell//enterprise0/ run-e0.xml

This command will generate your blockchain configuration in blockchains/0/0.xml. Example of run-xml files are found here::

    /postchain-mc/config/managed-mode/run-e0.xml
    /postchain-mc/config/managed-mode/run-c0.xml

Blockchain reference ID (BRID)
==============================
In folder blockchains/0/ you can also find the derived *brid* for your blockchain: blockchains/0/brid.txt. You need this to put in your prov.cfg.
Node configuration
The node configuration is split in node-config.properties (the same for all nodes) and private.properties (the node’s keypair). The node keypair is not the same as the provider keypair.
A node configuration file example is found here:
/postchain-mc/config/managed-mode/node-conf/node-config.properties

Ports and hosts
===============
Use open unique ports to avoid ssh and to not collide with testing. The ports used are
For inter-node communication: 5000 (instead of the default 9870)
For rest-API: 5001 (instead of the default 7740)
The hosts for the four nodes are:

* 10.240.0.55
* 10.240.0.56
* 10.240.0.57
* 10.240.0.58


Run your first node
===================

Now you have everything needed to start the node. Here are the steps.
Wipe db::

    ./postchain.sh wipe-db -nc conf0/node-config.properties

Add blockchain::

    ./postchain.sh add-blockchain -bc blockchains/0/0.xml -cid 0 -nc conf0/node-config.properties

Should we also do add-configuration? The command ``add-blockchain`` is a special case of the more general ``add-configuration``, with height set to zero. You do not need to do both, add-blockchain is enough.


Add peer::

    ./postchain.sh peerinfo-add -h 10.240.0.55 -nc conf0/node-config.properties -p 5000 -pk 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
    (./postchain.sh peerinfo-add -h localhost -nc conf0/node-config.properties -p 5000 -pk 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57)

The next step is good to somehow run in the background, so that the node continues to run even if you get disconnected. We can use for example ``systemd``. Here, ``screen`` is used::

    Screen -S bc0
    Screen -r bc0  (means reattach)
    Ctrl+a, d  (means detach)

So in screen bc0 we do::

    ./postchain.sh run-node -cid 0 -nc conf0/node-config.properties

Great! The node is running with chain ID 0.


Manage bc0 using the Management Client (pmc)
=============================================

In Enterprise0, the providers have the power. Only providers can make updates. For it all to start we therefore need an initial provider that in the beginning has all the management power. With the command below, this first provider is registered and enabled.::

    ./pmc-e0.sh init -cfg ../postchain-node/prov.cfg

Problems? Check that ``brid``  in your prov.cfg is the same as in ``/blockchains/0/brid.txt``

To add management of a blockchain, at least one managed node is needed. Therefore first add a managed node, making enterprise0 (bc0) aware of the Node0.  The provider of the node does (NB: If you are not the initial provider, the initial provider must first propose and enable you)::

    ./pmc-c0.sh add-node -cfg ../postchain-node/prov.cfg -p 5000 -k 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 -h 10.240.0.55
    (./pmc-e0.sh add-node -cfg ../postchain-node/prov.cfg -p 5000 -k 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 -h localhost)

Now we can add management of bc0, so that bc0 becomes aware of itself::

    ./pmc-e0.sh propose-blockchain -bc ../postchain-node/blockchains/0/0.xml -cfg ../postchain-node/prov.cfg -n 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

Plus vote.


Adding a new node
=============================================

We want to add a new node to bc0 (node1). A node that is owned by another provider.

First provider adds a new provider
-----------------------------------

Initial provider first registers ai-se-2 as provider and enables the new provider.::

    ./pmc-e0.sh propose-provider -cfg ../postchain-node/prov.cfg -pk 036C9145D9F535ED54AE942DD581E19DFFF6FDDAA98568BB936E41A23C356AF413

Get proposal index::

    ./pmc-e0.sh list-proposals-since  -cfg ../postchain-node/prov.cfg

Vote::

    ./pmc-e0.sh vote -idx index -cfg ../postchain-node/prov.cfg
    ./pmc-e0.sh propose-enable-provider -cfg ../postchain-node/prov.cfg -pk 036C9145D9F535ED54AE942DD581E19DFFF6FDDAA98568BB936E41A23C356AF413
    ./pmc-e0.sh list-proposals-since  -cfg ../postchain-node/prov.cfg
    ./pmc-e0.sh vote -idx index -cfg ../postchain-node/prov.cfg

Note that with two active (enabled) providers, NP=2, we need NP/2 + 1 = 2 approval votes. So both providers must vote yes on future proposals, before they take on effect.

Initialize
-----------------------------------

Provider initilaizes its node (add-bc, and add 2(!) peerinfo)::

    ./postchain.sh add-blockchain -cid 0 -bc blockchains/0/0.xml -nc conf1/node-config.properties

    ./postchain.sh peerinfo-add -h 10.240.0.56 -nc conf1/node-config.properties -p 5000 -pk 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9
    ./postchain.sh peerinfo-add -h 10.240.0.55 -nc conf1/node-config.properties -p 5000 -pk 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

Problems? Try ``./postchain wipe-db -nc``. For example if you didn’t add the right bc with the correct brid. (Add-blockchain with -f does not seem to work?.

Pubkey node ai-se-4::

    03ef3f5be98d499b048ba28b247036b611a1ced7fcf87c17c8b5ca3b3ce1ee23a4

Pubkey node ai-se-3::

    03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8

Register Node
----------------

Now the new provider can register its node in bc0::

    ./pmc-e0.sh add-node -k 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9 -cfg ../postchain-node/prov.cfg -h 10.240.0.56 -p 5000

Once you submit a blockchain configuration using ``./pmc-e0.sh propose-blockchain``, enterprise0 (bc0) code will delete the original signer list and add signers corresponding to the node list. So it needs to know the node before the blockchain can be added.

Should I, or must I add my node before I synchronize?
------------------------------------------------------


In fact, it might be necessary to do add-node before synchronization (run-node) in some cases because we have a rather odd rule: a machine with higher pubkey contacts machine with lower pubkey.
So what happens is that if pubkey of ai2 is lower than pubkey of ai1, it will not dare to contact ai1 first. :)
So you need to add it to chain0, so that ai1 would contact it. Like this:
When you create configuration for pmc-c0, you can give a link to REST API of your first machine. (i.e. the first machine in the cluster).
Then pmc-c0 add-node creates a record in the chain 0 currently managed by ai1 alone.
Even if you do synchronization before add-node, it will be ai0 (ai-se-1) that executes the transaction, since it is the only node that produces blocks. Before the command add-node, ai-se-2 is just a replica, and nodes are not listening for transactions from replicas. For ai-se-1 to listen to the command from ai-se-2, I changed in the replicas prov.cfg from api.url = http://localhost:7740 to http://10.240.0.55:7740. Thus, We communicate with the Rest API via ai-se-1, since there is no node (or API running on our own machine yet. Or at least no node that ai-se-1 thinks is worth listening to)?
Finally, I've successfully added my second node! Should I now change back in prov.cfg?
I can now do list-nodes and get-node-info etc.
I can also get from bc0 the configuration of a given blockchain at a given height (e.g. 4)::

    ./pmc-e0.sh get-blockchain-configuration -cfg ../postchain-node/prov.cfg -brid 9C1F485A1A3157CC29698F046DB804712FF7C385C5194A7F0D619DEE85153A41 -h 4

If I want to run my node with the new configuration I do ./postchain.sh add-configuration? No, not at all! It is done automatically when configuration is changed/updated.

Make me a signer
------------------------------------------------------


Still, my new node is not a signer. When a provider does ``pmc-e0.sh propose-blockchain``, the provided list of nodes are made signers automatically. But when adding a node separately, the table blockchain_signer_node must explicitly updated. It is not included in add-node.
So a provider does::

    ./pmc-e0.sh propose-add-blockchain-signers -cfg ../postchain-node/prov.cfg -brid 	 -n 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9


Add a new blockchain: bcai
===========================

Generate blockchain configuration.::

    ./multigen.sh -d rell-src-dir/aisweden/ runai.xml -o blockchainai

Want to add both nodes directly in the signer node list? No problem, just enter them as a comma separated list, no spaces.::

    ./pmc-e0.sh propose-blockchain -bc ../postchain-node/blockchainai/blockchains/100/0.xml -cfg ../postchain-node/prov.cfg -n 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57,035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9

NB: You cannot add a bc without at least one signer. Signers given in the blockchain configuration file are ignored. 

Do you want to add more signer after that the bc is added? Do this::

    ./pmc-e0.sh propose-add-blockchain-signers -cfg ../postchain-node/prov.cfg -brid 25095786FC38349095AD3E5326279D308BBB5932947594C623E9DB46A78848F9 -n 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9

Great! Now we have two signers for bcai as well. Now, do I also need to post add-blockhain on the other node (ai1)? Nope, that’s the beauty of Managed Mode...

Update blockchain configuration
=================================

Can I add new configuration “on the fly”? So I do not need to restart node? Yes, it is updated automatically in managed mode.
I will now make some changes/updates in the behavior of bc0 and add this configuration with pmc-e0 propose-configuration. I generated a new 0.xml with ``./multigen:``::

    ./multigen.sh -d rellslimmed/src run0.xml

And then added the new configuration at a height > current_height.::

    ./pmc-e0.sh propose-configuration -bc ../postchain-node/blockchains/0/0.xml -cfg ../postchain-node/prov.cfg -brid 25095786FC38349095AD3E5326279D308BBB5932947594C623E9DB46A78848F9 -h 500

NB If height < currentHeight, command will fail.::

    ./pmc-e0.sh propose-configuration -bc ../postchain-node/blockchainai/blockchains/100removed-row/0.xml -cfg ../postchain-node/prov.cfg -brid 3EB6181B3107568F6656E603FC02C240038CEB2D5F9D1ED13D620450A19C992E -h 17100

Plus vote.