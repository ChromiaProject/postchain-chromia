.. include:: ../example/introduction.rst

Initial Provider
================
The initial provider can vote “yes” to the first blockchain, bc0, and is a module argument to the rell module, defined in run.xml. Generate your provider keypair with:

Generate keypair for provider management console::

    ./pmc.sh keygen --save config/prov1.cfg

Update your run.xml with the corresponding public key. We used this pubkey of initial provider::

    <arg key="initial_provider">
        <bytea>03A692CDB2FD63037D883804A2028C5FBFD5E8A7E20E08BC387071220F8AD06710</bytea>
    </arg>

.. include:: ../example/setup.rst

Manage bc0 using the Management Client (pmc)
=============================================

In Enterprise0, the providers have the power. Only providers can make updates. For it all to start we therefore need an initial provider that in the beginning has all the management power. With the command below, this first provider is registered and enabled.::

    ./pmc.sh initialize -cfg config/prov1.cfg

To add management of a blockchain, at least one managed node is needed. Therefore first add a managed node, making enterprise0 (bc0) aware of the Node0.  The provider of the node does (NB: If you are not the initial provider, the initial provider must first propose and enable you)::

    ./pmc.sh add-node -cfg config/prov1.cfg -p 9870 -k 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 -h localhost

Now we can add management of bc0, so that bc0 becomes aware of itself::

    ./pmc.sh propose-blockchain -bc out/blockchains/0/0.xml -cfg config/prov1.cfg -n 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

Vote for this proposal.

.. hint::
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

.. hint::
    :doc:`/enterprise0/voting`

Then activates it with another proposal::

    ./pmc.sh propose-enable-provider -cfg config/prov1.cfg -pk <prov2-pubkey>

Note that with two active (enabled) providers, NP=2, we need NP/2 + 1 = 2 approval votes. So both providers must vote yes on future proposals, before they take on effect.

Initialize
-----------------------------------

Provider 2 initializes its node by adding the blockchain and peerinfos of itself and node 1. This can be done in one step using::

    ./run.sh 1 reset

Problems? Try ``./postchain.sh wipe-db -nc``. For example if you didn’t add the right bc with the correct brid.

Add Node to the network
---------------------------

The initial provider can register the new node to bc0::

    ./pmc.sh add-node -cfg config/prov1.cfg -h <new-node-host> -p <new-node-port> -k <new-node-pubkey>

Make the new node a signer of bc0
------------------------------------------------------

Provider 2 will propose node 1 as a signer. This tx must be sent to an existing signer signer, in this case n0. This means provider 2 must set the ``api-url`` path to point at n0 url until it has become a signer.
So a provider does::

    ./pmc.sh propose-add-blockchain-signers -cfg config/prov2.cfg -brid <bc0-brid> -n <new-node-pubkey>

And both providers must vote on this proposal.

.. note:: Do I have to add provider before adding a new node?

    No, it is also possible to add the new node and propose it as a signer before adding the new provider. The benefit of this is that less votes are needed to add the node as signer (in this case 1 in stead of two). The drawback is that then it is provider 1 that has to do all the work of adding the node. Consider providers as persons and nodes as machines they are hosting.

Add a new blockchain: city
============================

Generate blockchain configuration::

    multigen.sh -d app/src/ app/config/run.xml -o app/out

This can be done using the supplied script::

    ./app/generate.sh

Propose the new chain to bc0. This can be done as either of the providers and to any node that is a signer of bc0. To make both nodes signers of the new chain, supply public keys to all nodes you want as signers when you propose the chain and vote for its proposal::

    ./pmc.sh propose-blockchain -bc app/out/blockchains/100/0.xml -cfg config/prov2.cfg -n <n1-pubkey>,<n2-pubkey>

.. note::
    You cannot add a bc without at least one signer. Signers given in the blockchain configuration file are ignored.

Do you want to add more signers after that the bc is added? Propose and vote for it::

    ./pmc.sh propose-add-blockchain-signers -cfg config/prov2.cfg -brid <bc0-brid> -n <nx-pubkey>

Great! Now we have two signers for ``city`` as well.

.. note::
    Do I also need to post add-blockhain on the other nodes?

    No, that’s the beauty of Managed Mode...

Update blockchain configuration
=================================

To update the configuration or source code of any of the chains, we generate a new blockchain configuration and propose it.

For example if I make changes to bc0 rell source, we first re-generate the configuration::

    ./generate.sh

And then add the new configuration at a height > current_height.::

    ./pmc.sh propose-configuration -bc out/blockchains/0/0.xml -cfg config/prov1.cfg -brid <bc0-brid> -h 500


.. warning::
    If height < currentHeight, the command will fail.

    If height is reached before consensus is acheived, the command will fail.

    Make sure to add the configuration at a height sufficiently far in the future so that it can be added properly.