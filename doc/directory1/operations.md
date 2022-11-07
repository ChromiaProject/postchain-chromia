# Operations

## Directory1

| Operation       | Permission | Rate limit    | Comment |
|-----------------|------------|---------------|---------|
| init            | any        | no(only once) |         |
| update_provider | self       | actions       |         |

## Common

| Operation                  | Permission                           | Rate limit | Comment                                                                                                                                                                |
|----------------------------|--------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| register_provider          | SP > NP > CNP >= CNP                 | actions    | SP can register NP, NP can register CNP, CNP can register CNP / TODO: Add a test                                                                                       |
| transfer_action_points     | any                                  | actions    | TODO: should be: SP > NP > CNP >= CNP                                                                                                                                  |
| add_node                   | no                                   | actions    | TODO: Should be only NP? But others can add replicas, right? Limit number of nodes? https://gitlab.com/chromaway/postchain-chromia/-/merge_requests/96#note_1157664476 |
| replace_node               | owned by                             | actions    |                                                                                                                                                                        |
| remove_node                | no                                   | actions    | TODO: Should be owned by or system permission?                                                                                                                         |
| update_node                | node provider                        | actions    ||
| promote_node_provider      | ?                                    | actions    | TODO: Add a test                                                                                                                                                       |
| add_container_replica      | no                                   | actions    | TODO: What is this? Redesign, https://gitlab.com/chromaway/postchain-chromia/-/merge_requests/96#note_1126303348                                                       |
| remove_container_replica   | no                                   | actions    | TODO: What is this? Should be owned by or system permission? Redesign.                                                                                                 |
| add_node_to_cluster        | cluster provider                     | actions    ||
| create_cluster             | node provider                        | actions    ||
| add_bc_replica             | node provider                        | actions    | TODO: should be CNP but with a max limit                                                                                                                               |
| remove_bc_replica          | node provider                        | actions    ||
| make_vote                  | voter set member                     | no         ||
| retract_vote               | voter set member                     | no         ||
| propose_cluster_provider   | cluster governor                     | actions    ||
| propose_provider_state     | SP > [SP, NP, CNP], NP > CNP         | actions    | NP can enable/disable CNP without voting. TODO: will disable all nodes as well (blocked by remove_node)?                                                               |
| propose_provider_is_system | any                                  | actions    | can also demote                                                                                                                                                        |
| propose_container          | cluster governor                     | actions    |                                                                                                                                                                        |
| propose_container_limits   | cluster governor                     | actions    ||
| propose_remove_container   | cluster governor AND empty container | actions    ||
| propose_cluster_limits     | cluster governor                     | actions    ||
| propose_remove_cluster     | cluster governor AND empty cluster   | actions    ||
| propose_blockchain         | container deployer                   | actions    ||
| propose_configuration      | container deployer                   | actions    ||
| propose_blockchain_action  | container deployer                   | actions    | Note: Provider can propose to stop other blockchains in the container                                                                                                  |
| propose_update_voter_set   | voter set governor                   | actions    |                                                                                                                                                                        |
| anchor_block               | no                                   | no         | Old anchoring implementation. Attack vector? Hard to make but perhaps we need some verification?                                                                       |

