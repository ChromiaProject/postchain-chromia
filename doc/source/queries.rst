===========
Queries
===========

There are various information stored in the mama blockchain (bc0) about the network. Useful information about the network. It is available trough queires to the database via the pmc-e0 interface. Some (not all) of the queries are described below. To get a complete list run::

    ./pmc-e0.sh 

Get blockchain configuration
=============================
./pmc-e0.sh get-blockchain-configuration -cfg ../postchain-node/prov.cfg -brid 9C1F485A1A3157CC29698F046DB804712FF7C385C5194A7F0D619DEE85153A41 -h 4

Also future heights is OK.

List-blockchain-signers
=============================
./pmc-e0.sh list-blockchain-signers -cfg ../postchain-node/prov.cfg -brid 9C1F485A1A3157CC29698F046DB804712FF7C385C5194A7F0D619DEE85153A41 
GetNodeInfo
A replica is an inactive node (active = false).
 
GetProviderInfo
=============================
state = true. Does it mean enabled? Yes. active = enabled = state true.

List-blockchains-for-node
=============================

Command also works for replica nodes.

./pmc-e0.sh list-blockchains-for-node  -cfg ../postchain-node/prov.cfg -k 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9

25095786FC38349095AD3E5326279D308BBB5932947594C623E9DB46A78848F9
3EB6181B3107568F6656E603FC02C240038CEB2D5F9D1ED13D620450A19C992E

Listed blockchains successfully

GetHeight?
=============================
There is no pmc-command to get info on current height or number of blocks built per minute.
Don’t we want a command for get-last-height? And block generation rate as well. To know how fast the height is changing. To be able to set a good configuration update height.
