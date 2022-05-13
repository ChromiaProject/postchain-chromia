===========
Q&A
===========

What's a replica?
=============================

The official replicas for a given blockchain are the nodes in the replica node list. Only the provider of a node can add or remove it to/from the replica node list. (In Chromia0, also admin has this authority).

In addition to this, all nodes in the network are replicas to blockchain chroma0/enterprise0. If they are not signers of course.

What is an inactive blockchain?
===============================
Does inactive bcs exist? Yes. You can see them if you list blockchains with the flag -i (for include inactive) set. A blockchain  becomes inactive if you do CommandStopBlockchain. This command updates the blockchain_signer_node table without updating the configuration. Therefore the bc can be woken up again. Thus, even if the signers list is empty, the bc is not dead.

Note that this state can only be achieved with the CommandStopBlockchain, not by removing blockchain signers in other ways (removing nodes, disabling providers or removing signers).

What does it mean to disable a provider?
========================================
Disabling a provider means not only setting its state to active = false. It also removes its nodes as signers in all its blockchains (thus updating current and future configurations). Also the corresponding rows in the table blockchain_replica_node are deleted.

NB (again): Make sure that you do not remove the last signer of a blockchain.

How can I check what's in my database?
======================================

First make sure you know the name of you schema. It can be found in your node configuration.

On linux you can do this (with schema = ai_cluster).::

    sudo su - postgres
    psql postchain
    set search_path to ai_cluster ;
    postchain=# select * from c0.blocks;
    postchain=# select * from c0.blockchains;
    Ctr -d (leaving psql)
    logout (log out user postgres)

How many keys do I need and when to use which?
===============================================
You need two key pairs: a node key pair, and a provider key pair. Key pairs can be generated with `keygen` subcommand.

Provider Key pair
    You use this key pair whenever you need to prove who you are (that is, a provider with authorities to do stuff). You do that via the parameter -cfg with your prov.cfg file.
    
A prov.cfg file typically look like this::

    privkey: 4707C67377D5629AB3560ACD24F89C7273FE64C20BF1D6643A68D8E62051EC5E
    pubkey: 036C9145D9F535ED54AE942DD581E19DFFF6FDDAA98568BB936E41A23C356AF413
    mnemonic: either dilemma orphan use file essence snap scout snake chief check top divide crash amused lawsuit stool canyon olive range ginger cigar ramp spot
    api.url=http://10.240.0.55:7740
    brid=9C1F485A1A3157CC29698F046DB804712FF7C385C5194A7F0D619DEE85153A41

Node key pair
    This is for signing blocks. Public node key is also used to identify a node.

Remember, always keep your keys safe!

What's Anchoring?
==================
All nodes do not run all bcs, but they all run bc0. A provider (A) that proposes a config update of a bc (chainx) needs to be aware of the current height of x, so that he can set the new configuration height > current height. Since he is interested in x, he is probably also running chainx. Thus he could get the current height from his local database.
Suppose now that we have one more active provider (B). For the proposal to be applied, it must be approved by B. B is not herself running chainx but trusts in A and is therefore voting yes to his proposal on config update of x. Immediately , when her vote is registered the proposal is also applied (enough positive votes). But since she herself is not running chainx, its current heigth can not be found locally.

To ge access to block height of all blockchain, not just the ones that a node is running herself, they are anchored to bc0. Information on block heights of all managed blockchains are found in bc0.

How can I check which ports that are used?
==============================================
On linux you can do this::

    netstat -tulpan|grep LISTEN

How to use System-d for running a node in background?
================================================================

System-d can be set up to run your node in background and also restart it automatically after a restart or crash of your computer.::

    sudo nano  ../../etc/systemd/system/postchain.service
    sudo systemctl status postchain.service
    sudo systemctl start postchain.service
    sudo systemctl stop postchain.service

Reload is needed after changes.

What's the difference between Enterprise0 and Chomia0?
=========================================================

Enterprise0 and Chromia0 share quite a lot of functionality. Chromia is the public network and Enterpris0 is the Enterprise Level Solution, for use within a company or organization.


