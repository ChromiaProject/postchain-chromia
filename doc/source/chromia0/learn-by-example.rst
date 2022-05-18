.. include:: ../example/introduction.rst

Admin
================
The admin of the network is a module argument to the rell module, defined in run.xml. Generate your admin keypair with::

    ./pmc.sh keygen --save config/admin.cfg

Update your run.xml with the corresponding public key. We used this pubkey of initial provider::

    <arg key="admin">
        <bytea>03A692CDB2FD63037D883804A2028C5FBFD5E8A7E20E08BC387071220F8AD06710</bytea>
    </arg>

.. include:: ../example/setup.rst

Manage bc0 using the Management Client (pmc)
=============================================

In Chromia0, only the admin can add and enable providers, update signer lists and add blockchains. Providers can add nodes. This means a new provider will ask admin for permission to become a provider. He will then add its nodes to the network and ask the admin to make the node a signer of a blockchain. Any user who wants to start a new blockchain will ask the admin.

Create a provider keypair similar to ``admin.cfg`` and store it in ``config/prov1.cfg``. Admin will now register and enable this provider::

    ./pmc.sh register-provider -cfg config/admin.cfg -k <prov1-pubkey>
    ./pmc.sh enable-provider -cfg config/admin.cfg -k <prov1-pubkey>

To add management of a blockchain, at least one managed node is needed. Therefore first add a managed node, making chromia0 (bc0) aware of the Node0. The provider does::

    ./pmc.sh add-node -cfg config/prov1.cfg -p 9780 -k 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 -h localhost

Now the admin can add management of bc0, so that bc0 becomes aware of itself::

    ./pmc.sh add-blockchain -bc out/blockchains/0/0.xml -cfg config/admin.cfg -n 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57

Adding a node to the network
=============================================

Now we add a new node to the network. In this example, the node is owned by another provider.
The second provider creates its own keypair and and adds the brid and api-url to a file ``config/prov2.cfg``.

Admin adds the new provider
-----------------------------------

Admin first registers provider 2 and enables the new provider::

    ./pmc.sh register-provider -cfg config/admin.cfg -k <prov2-pubkey>
    ./pmc.sh enable-provider -cfg config/admin.cfg -k <prov2-pubkey>

Initialize
-----------------------------------

Provider 2 initializes its node by adding the blockchain and peerinfos of itself and node 1. This can be done in one step using::

    ./run.sh 1 reset

Problems? Try ``./postchain.sh wipe-db -nc``. For example if you didn’t add the right bc with the correct brid.

Add Node to the network
---------------------------

The new provider can register the new node to bc0::

    ./pmc.sh add-node -cfg config/prov2.cfg -h <new-node-host> -p <new-node-port> -k <new-node-pubkey>

.. note::
    This has to be sent to node 1 since it is the only signer node so far. Configure ``prov2.cfg`` to point at n0 api-url.

Make the new node a signer of bc0
------------------------------------------------------

Admin will add node 1 as a signer. This tx must be sent to an existing signer signer, in this case n0.
Admin does::

    ./pmc.sh add-blockchain-signers -cfg config/admin.cfg -brid <bc0-brid> -s <new-node-pubkey>

Add a new blockchain: city
============================

Generate blockchain configuration::

    multigen.sh -d app/src/ app/config/run.xml -o app/out

This can be done using the supplied script::

    ./app/generate.sh

Add the new chain to bc0. This must be done as admin and to any node that is a signer of bc0. To make both nodes signers of the new chain, supply public keys to all nodes you want as signers when you add the blockchain::

    ./pmc.sh add-blockchain -bc app/out/blockchains/100/0.xml -cfg config/admin.cfg -n <n1-pubkey>,<n2-pubkey>

.. note::
    You cannot add a bc without at least one signer. Signers given in the blockchain configuration file are ignored.

Do you want to add more signers after that the bc is added? Admin can add it::

    ./pmc.sh add-blockchain-signers -cfg config/admin.cfg -brid <bc0-brid> -n <nx-pubkey>

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

    ./pmc.sh add-configuration -bc out/blockchains/0/0.xml -cfg config/admin.cfg -brid <bc0-brid> -h 500


.. warning::
    If height < currentHeight, the command will fail.

    Make sure to add the configuration at a height sufficiently far in the future so that it can be added properly.