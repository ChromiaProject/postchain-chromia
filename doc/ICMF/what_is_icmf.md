# What is ICMF?

Inter-Chain Messaging Facility (ICMF) enables cross-blockchain communication between two or more chains without the need for any client interaction. The message body can be any arbitrary data encoded as GTV.

Messages are injected into the receiver chains via special transactions. The receiver chain will need to be configured with a GTXModule that implements the ICMF message operation to act on the received message.

For more detailed information on how to use it, please see: [ICMF user guide](icmf_user_guide.md)

ICMF is poll-based. Meaning that sender chains only notify that they have messages by including message information in block headers and then responding when receivers query for messages. It is up to receiver chains to poll for new messages.

## Topic based communication
Messages are sent on topics. Receiving chains will need to be configured with a list of the topics they are interested in.

### Global topics
Chains running in different clusters can communicate via global topics. Receiver chains only need to configure the global topic that they are interested in listening to. This functionality is made possible by cluster anchoring chains aggregating information about which chains that have sent messages on global topics.

Receiver chains will simply poll the cluster anchoring chains for all clusters to see if any chain has sent a message on a topic.

**Note:** To avoid potential spamming issues it is currently only system chains that are allowed to send messages on global topics.

### Local topics
Messages can be sent on local topics (cluster anchoring chain will ignore any such messages). In this case the receiver chains has to be configured with the blockchain RID of the sender chain as well as the relevant topic.

Receivers will poll sending chains directly to determine if there are any new messages.

## Message validation
For each message that is sent by a chain at a specific height the hash of the message body is included in the block header of the sender chain at that height. The hashes are grouped by topic.

Receiver chains will fetch messages as well as the block headers and witnesses for the block heights on which the messages was sent. The receiver chain can then validate the messages by firstly verifying the block headers by checking the signatures in the witness data and secondly by comparing the message bodies to the corresponding hashes in the block header.

## Message delivery guarantees
Messages are guaranteed to be delivered once and only once to receiving chains. There are no guarantees about delivery times. The messages are only guaranteed to be in order per sender chain.

## Use cases
We use ICMF internally for configuration updates. Use cases are probably very similar to the ones for ICCF. However, ICMF has the benefit of not relying on any client interaction. So it is useful for cross-chain communication that can not rely on a client. Since it is topic based it is also easier to set up one-to-many communication, unlike for ICCF where the client would have to present a confirmation proof to ALL chains of interest. The fact that the message format is completely flexible is also an advantage over ICCF where the receiving chain has to interpret a whole transaction.

The drawback against ICCF is the risk for spamming since the messages are injected via special transactions that the receiver chain MUST process. Unlike ICCF which will simply utilize the regular transaction queue and will be subject to the capacity and rate limits that it has.

### Configuration updates
ICMF is currently used to facilitate updates of blockchain configuration. When a configuration update for a chain is proposed to the directory chain it will be considered a pending configuration update. Once the configuration has been applied by the chain it will send an ICMF message to the directory chain that it has either applied the configuration update at a certain height or failed to do so. The directory chain will store at which height the configuration was applied or mark it as failed.