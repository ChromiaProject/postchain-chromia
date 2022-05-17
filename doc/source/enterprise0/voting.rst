How do I vote?
=================
Providers together decide on blockchain configuration updates. Power is decentralized. To vote on a proposal you need to know its identity (index)::

    ./pmc.sh list-proposals-since  -cfg config/prov1.cfg
    ./pmc.sh get-proposal -idx 17 -cfg config/prov1.cfg
    ./pmc.sh vote -idx 17 -cfg config/prov1.cfg

To reject a proposal, set flag ``--approve`` to false::

    ./pmc.sh vote -idx 17 -cfg ../postchain-node/prov.cfg --approve false
