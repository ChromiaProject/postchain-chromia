How do I vote?
=================
Providers together decide on blockchain configuration updates. Power is decentralized. To vote on a proposal you need to know its identity (index).::

    ./pmc-e0.sh list-proposals-since  -cfg ../postchain-node/prov.cfg
    ./pmc-e0.sh get-proposal -idx 17 -cfg ../postchain-node/prov.cfg
    ./pmc-e0.sh vote -idx 17 -cfg ../postchain-node/prov.cfg

To reject a proposal, set flag --aprove to false.::

    ./pmc-e0.sh vote -idx 17 -cfg ../postchain-node/prov.cfg --aprove false
