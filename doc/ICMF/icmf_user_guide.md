# ICMF user guide

This documentation is targeted to dapp-developers that wants to write a dapp that sends and/or receives ICMF messages.

## Topics
Messages are sent and received on topics. Note that all topics need to be prefixed with `L_`. This is to indicate that the message is sent on a _local_ topic. Only system chains are allowed to send topics on a _global_ level.

## Sending messages

### Configuration
You will need to add a GTX module to your blockchain configuration.

```
config:
  gtx:
    modules:
      - "net.postchain.d1.icmf.IcmfSenderGTXModule"
```

You can also modify the default message query limit of your blockchain. If your chain is sending very large messages it
may be a good idea to lower it in order to not get spammed with huge queries:

```
config:
  icmf:
    sender:
      message_query_limit: 100 # Default is 100
```

### Rell code

To include the ICMF module in your rell project, specify in your config file:
```yaml
libs:
  com.chromia.icmf:
    version: 1.102.2
```
Then use `chr install` to install the ICMF library.

Now you can import the `icmf` module and call the function `send_message(topic: text, body: gtv)` wherever you want to send a message.

Receivers will read your message if they are configured to listen on the topic that you sent it on. Notice that the body is of type gtv so you can send any structure you like (just make sure receivers can parse it).

## Receiving messages

### Configuration
You will need to add a GTX module and a synchronization infrastructure extension to your blockchain configuration.

```
config:
  gtx:
    modules:
      - "net.postchain.d1.icmf.IcmfReceiverGTXModule"
  sync_ext:
    - "net.postchain.d1.icmf.IcmfReceiverSynchronizationInfrastructureExtension"
```

### Rell code
You need to either implement the message receive operation in rell to receive messages. Example:

```
operation __icmf_message(sender: byte_array, topic: text, body: gtv) {
   // Parse and handle the message here
}
```

The `sender` parameter here is the blockchain-rid of the sender chain.

Or use the Rell library:

```yaml
import lib.icmf.receiver.*;
```

And then extend the following function:

```
@extendable function receive_icmf_message(sender: byte_array, topic: text, body: gtv) {}
```

If you need height and timestamp of the sent message you can use the metadata receiver module:

```yaml
import lib.icmf.metadata_receiver.*;
```

And then extend the following function:

```
@extendable function receive_icmf_message(sender: byte_array, sender_height: integer, sender_timestamp: integer, topic: text, body: gtv) {}
```

**Note:** Extracting the height and timestamp can incur a small performance penalty

### Rate-limit received messages

You can implement a special query with mount name `icmf.receiver_rate_limit` to rate-limit incoming messages in local topics. 
This query is called once per block and pipe (combination of sender and topic), and if it throws (e.g. by `require()` or 
`rell.error()`), no messages from that pipe will be processed in the current block. The query needs to return an empty 
GtvDict (which might be filled with properties in the future). 

```
@mount("icmf.")
query receiver_rate_limit(sender: byte_array, topic: text): map<text, gtv> {
    // do checks here
    return [:];
}
```

### Topic and sender configuration
You also need to configure which topics and sender chains you want to listen to.

```
config:
  icmf:
    receiver:
      local: # List your chains and topics here
        - bc-rid: x"0000000000000000000000000000000000000000000000000000000000000001"
          topic: "L_topic1"
        - bc-rid: x"0000000000000000000000000000000000000000000000000000000000000002"
          topic: "L_topic2"
          skip-to-height: 10 # Skip any messages sent by chain on this topic until this height
        - etc.
```

### Listening to global topics

Only system chains are allowed to send messages on global topics but all chains can listen to them.

```
config:
  icmf:
    receiver:
      global:
        topics: # List your topics here
          - "G_topic1"
          - "G_topic2"
          - etc.
```

To only listen to specific sender chains:

```
config:
  icmf:
    receiver:
      global:
        blockchains: # List your chains topics here
          - bc-rid: x"0000000000000000000000000000000000000000000000000000000000000001"
            topic: "G_topic1"
          - bc-rid: x"0000000000000000000000000000000000000000000000000000000000000002"
            topic: "G_topic2"
            skip-to-height: 10 # Skip any messages sent by chain on this topic until this height
          - etc.
```

### Combining ICMF with other extensions

ICMF will normally try to fit as many messages as possible into the block. When running in combination with other
extensions it might be necessary to tweak the amount of space that it leaves for them to inject operations.

```
config:
  icmf:
    receiver:
      special-tx-margin-bytes: 102400 # Default 100 KiB
```

### Limit number of messages per block

Sometimes we may not be able to process more than X messages per block in a timely manner. In that case we can
configure the maximum number of messages that should be processed per block.

```
config:
  icmf:
    receiver:
      message-limit: 100 # Default 100 messages per block
```

### Trigger block building

Block building will be triggered after some time if there are waiting messages. This will currently only work for 
inter-cluster messages. 

```
config:
  icmf:
    receiver:
      message-check-interval-ms: 1000 # Default 1 second
```
