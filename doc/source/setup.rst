==========
Setup
==========

Requirements
======================
- Postchain distribution with rell module

Package content
=====================
The example package contains

.. code-block:: shell

    .
    ├── app
    │   ├── config
    │   │   └── run.xml
    │   ├── generate.sh
    │   └── src
    │       └── main.rell
    ├── config
    │   ├── common.properties
    │   ├── config.0.properties
    │   ├── config.1.properties
    │   ├── config.2.properties
    │   ├── config.3.properties
    │   └── run.xml
    ├── doc
    │   └── index.html
    ├── sources.tar.gz
    ├── generate.sh
    ├── dist.tar.gz
    ├── pmc.sh
    ├── postchain-debug.sh
    ├── postchain.sh
    └── run.sh

Setup
==================
- export the path to the postchain distribution to your environment:

.. code-block:: shell

    export POSTCHAIN_DIR=/path/to/postchain-node/

- Unpack the management chain `sources` and postchain mc `dist` tarballs.
- Generate blockchain configuration for bc0

.. code-block:: shell

    ./generate.sh

You will now have a folder called ``out`` which contains the configuration of bc0

.. code-block:: shell

    out
    ├── blockchains
    │   └── 0
    │       ├── 0.gtv
    │       ├── 0.xml
    │       └── brid.txt
    └── node-config.properties

The files ``0.gtv`` and ``0.xml`` found in the folder ``0`` is the blockchain configuration (in two formats) for chain 0 at height 0 (`blockchains/<chain-id>/<height>.xml`)

.. note::

    Disregard the file ``node-config.properties`` file, we will be using the ones found in ``config`` folder in stead.

.. note::

    The ``brid.txt`` will not the the brid of ``bc0`` for this dapp since ``bc0`` will create its own brid for this chain when it manages itself.