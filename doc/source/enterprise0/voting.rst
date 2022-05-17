How do I vote?
=================
Providers together decide on blockchain configuration updates. Power is decentralized. To vote on a proposal you need to know its identity (index)::

    ./pmc.sh list-proposals-since -cfg prov.cfg
    ./pmc.sh get-proposal -cfg prov.cfg -idx 17
    ./pmc.sh vote -cfg prov.cfg -idx 17

To reject a proposal, set flag ``--approve`` to false::

    ./pmc.sh vote -cfg prov.cfg -idx 17 --approve false
