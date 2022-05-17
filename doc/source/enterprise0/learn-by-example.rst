=============================================
Learn by an example: The AI project
=============================================

In this section you can learn how to set up your on blockchain network by studying an concrete example. The example project is called AI.

The cluster has four nodes. The first node Ai0 is the initial provider node. Bc0 is set up on this first node and also added by the Management client (postchain-mc) so that bc0 will become aware of itself. Then we can in a convenient  way update configuration, add a second blockchain to be managed by bc0: bcai. Also more nodes can be added and be made signers.

There are quite a few steps here, we’ll take them one at a time.


Initial Provider
================
The initial provider can vote “yes” to the first blockchain, bc0, and is a module argument to the rell module, defined in run.xml. Generate your provider keypair with:

Generate keypair for provider management console::

    ./pmc.sh keygen --save config/prov1.cfg

Update your run.xml with the corresponding public key. We used this pubkey of initial provider::

    <arg key="initial_provider">
        <bytea>03A692CDB2FD63037D883804A2028C5FBFD5E8A7E20E08BC387071220F8AD06710</bytea>
    </arg>

Generate blockchain configuration
=============================================
The blockchain configuration is generated with the script ``multigen.sh``. The configuration is generated from

- run.xml
- rell source code

In this example you can run ``generate.sh`` supplied in this package to generate blockchain configuration for ``bc0``.

Blockchain reference ID (BRID)
==============================
In the folder ``out/blockchains/0/`` you can find the blockchain configuration and the derived *brid* for the blockchain. The brid must be set in ``prov.cfg`` to be able to communicate with this chain::

    blockchain-rid=54D9D994C3563CBC1577E1E45E94E35647D2D46ED24FCD8A5C29F96F240A4BC8

Rest API
=============================

Finally you need to specify the url to the rest api of the node you want to communicate with. For the initial step this needs to be the first node but once the network is set up this can be any node of the network. If you are running the node locally you can specify it as::

        api-url=http://127.0.0.1:7740

Run the first node
===================

To start the first node you need to perform the following steps:
Wipe db::

    ./postchain.sh wipe-db -nc config/config.0.properties

Add blockchain::

    ./postchain.sh add-blockchain -bc out/blockchains/0/0.xml -cid 0 -nc config/config.0.properties

Should we also do add-configuration? The command ``add-blockchain`` is a special case of the more general ``add-configuration``, with height set to zero. You do not need to do both, add-blockchain is enough.


Add peer::

    # Add its own info
    ./postchain.sh peerinfo-add -h 10.240.0.55 -nc conf0/node-config.properties -p 5000 -pk 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

In this example, you can use ``./run.sh 0 reset`` to perform the above operations on node 0.

The next step is good to somehow run in the background, so that the node continues to run even if you get disconnected. We can use for example ``systemd``. Here, ``screen`` is used::

    screen -S bc0
    screen -r bc0  (means reattach)
    Ctrl+a, d  (means detach)

So in screen bc0 we do::

    ./postchain.sh run-node -cid 0 -nc conf0/node-config.properties
    or
    ./run.sh 0 run

Great! The node is running with chain ID 0.


Manage bc0 using the Management Client (pmc)
=============================================

In Enterprise0, the providers have the power. Only providers can make updates. For it all to start we therefore need an initial provider that in the beginning has all the management power. With the command below, this first provider is registered and enabled.::

    ./pmc.sh init -cfg config/prov1.cfg

To add management of a blockchain, at least one managed node is needed. Therefore first add a managed node, making enterprise0 (bc0) aware of the Node0.  The provider of the node does (NB: If you are not the initial provider, the initial provider must first propose and enable you)::

    ./pmc.sh add-node -cfg config/prov1.cfg -p 9780 -k 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 -h localhost

Now we can add management of bc0, so that bc0 becomes aware of itself::

    ./pmc.sh propose-blockchain -bc out/blockchains/0/0.xml -cfg config/prov1.cfg -n 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

Vote for this proposal.

:doc:`/enterprise0/voting`

Adding a node to the network
=============================================

Now we add a new node to the network. In this example, the node is owned by another provider.
The second provider creates its own keypair and and adds the brid and api-url to a file ``config/prov2.cfg``.

First provider adds a new provider
-----------------------------------

Initial provider first registers provider 2 and enables the new provider::

    ./pmc.sh propose-provider -cfg config/prov1.cfg -pk <prov2-pubkey>

Provider 1 now votes for this proposal to add the provider.

:doc:`/enterprise0/voting`

Then activates it with another proposal::

    ./pmc.sh propose-enable-provider -cfg config/prov1.cfg -pk 027DE85A4FB4ED49F0208E7AEFDD3E926D18EE913A67A37CB2C6AF6DAC04CC21A9

Note that with two active (enabled) providers, NP=2, we need NP/2 + 1 = 2 approval votes. So both providers must vote yes on future proposals, before they take on effect.

Initialize
-----------------------------------

Provider 2 initializes its node in the same way as provider 1 (add-bc, and add peerinfo for itself and initial node)::

    ./run.sh 1 reset

Problems? Try ``./postchain wipe-db -nc``. For example if you didn’t add the right bc with the correct brid.

Add Node to the network
----------------

The initial provider can register the new node to bc0::

    ./pmc.sh add-node -k <node1-pubkey> -cfg config/prov1.cfg -h <node1-host> -p <node1-port>

Make the new node a signer of bc0
------------------------------------------------------

Provider 2 will propose node 1 as a signer. This tx must be sent to an existing signer signer, in this case n0. This means provider 2 must set the ``api-url`` path to point at n0 url until it has become a signer.
So a provider does::

    ./pmc.sh propose-add-blockchain-signers -cfg config/prov2.cfg -brid <bc0-brid> -n <new-node-pubkey>

And both providers must vote on this proposal.

Do I have to add provider before adding a new node?
===================================================

No, it is also possible to add the new node and propose it as a signer before adding the new provider. The benefit of this is that less votes are needed to add the node as signer (in this case 1 in stead of two). The drawback is that then it is provider 1 that has to do all the work of adding the node. Consider providers as persons and nodes as machines they are hosting.

Add a new blockchain: bcai
===========================

Generate blockchain configuration.::

    ./multigen.sh -d rell-src-dir/aisweden/ runai.xml -o blockchainai

Want to add both nodes directly in the signer node list? No problem, just enter them as a comma separated list, no spaces.::

    ./pmc-e0.sh propose-blockchain -bc ../postchain-node/blockchainai/blockchains/100/0.xml -cfg ../postchain-node/prov.cfg -n 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57,035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9

NB: You cannot add a bc without at least one signer. Signers given in the blockchain configuration file are ignored. 

Do you want to add more signer after that the bc is added? Do this::

    ./pmc-e0.sh propose-add-blockchain-signers -cfg config/prov2.cfg -brid 25095786FC38349095AD3E5326279D308BBB5932947594C623E9DB46A78848F9 -n 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9

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