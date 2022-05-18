===========
Queries
===========

There is various information about the network stored in the management blockchain (bc0). It is available through queries to the database via the pmc interface. Some (not all) of the queries are described below. To get a complete list run::

    ./pmc.sh

Get blockchain configuration
=============================
.. code-block:: shell

    ./pmc.sh get-blockchain-configuration -cfg prov.cfg -brid <bc0-brid> -h <height>

Future heights is also OK.

List blockchain signers
=============================

.. code-block:: shell

    ./pmc.sh list-blockchain-signers -cfg prov.cfg -brid <bc-brid>

Get node info
=============================
.. code-block:: shell

    ./pmc.sh get-node-info -cfg prov.cfg -k <node-pubkey>

A replica is an inactive node (active = false).
 
Get provider info
=============================
.. code-block:: shell

    ./pmc.sh get-provider-info -cfg prov.cfg -k <provider-pubkey>

Provider is active if active = enabled = state = true.

List blockchains for node
=============================

Command also works for replica nodes.

.. code-block:: shell

    ./pmc.sh list-blockchains-for-node  -cfg prov.cfg -k <node-pubkey>
