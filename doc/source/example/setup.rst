Generate blockchain configuration
=============================================
The blockchain configuration is generated with the script ``multigen.sh``. The configuration is generated from

- run.xml
- rell source code

In this example you can run ``generate.sh`` supplied in this package to generate blockchain configuration for ``bc0``.

Blockchain reference ID (BRID)
==============================
In the folder ``out/blockchains/0/`` you can find the blockchain configuration and the derived *brid* for the blockchain. The brid must be set in the config file to be able to communicate with this chain::

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

.. note::
    Should we also do ``add-configuration``? The command ``add-blockchain`` is a special case of the more general ``add-configuration``, where height set to zero. This means that if you run ``add-blockchain``, you don't need to add the configuration.


Add peer::

    # Add its own info
    ./postchain.sh peerinfo-add -nc config/config.0.properties -h <n0-host> -p <n0-port> -pk <n0-pubkey>

In this example, you can use ``./run.sh 0 reset`` to perform the above operations on node 0.

The next step is good to somehow run in the background, so that the node continues to run even if you get disconnected. We can use for example ``systemd``. Here, ``screen`` is used::

    screen -S n0
    Ctrl+a, d  (means detach)
    screen -r n0  (means reattach)

So in screen n0 we do::

    ./postchain.sh run-node -cid 0 -nc conf0/node-config.properties
    or
    ./run.sh 0 run

Great! The node is running with chain ID 0.
