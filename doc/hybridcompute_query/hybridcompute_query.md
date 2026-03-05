# Hybrid Compute Query Engine

The Hybrid Compute Query Engine allows a blockchain to execute queries on itself as part of a hybrid compute request and store the results on-chain.

## Configuration

To enable the Query Compute Engine, you need to add it to the `hybridcompute` engines list and optionally provide configuration for it.

```yaml
blockchains:
  my_blockchain:
    module: main
    config:
      hybridcompute:
        engines:
          - "net.postchain.hybridcompute.engine.QueryComputeEngine"
      hybridcompute_query:
        timeout_seconds: 5 # Optional, defaults to 3 seconds.
```

## Rell Usage

### Install the library:

The `hybridcompute_query` library provides a convenient wrapper for interacting with the Query Compute Engine. Add it to your `chromia.yml` and run `chr install`:

```yaml
libs:
  com.chromia.hybridcompute_query:
    version: 1.0.0 # Find latest by running chr library view com.chromia.hybridcompute_query
```

### Import the library:

```rell
import hcq: lib.hybridcompute_query;
```

### Submit a query request:

To start a query computation, use `submit_query_request`. You must provide a unique ID, the name of the query, and its arguments as a map.

```rell
operation example_operation(id: text, argument_text: text) {
    hcq.submit_query_request(
        id, 
        "get_text_length", 
        ["text": argument_text.to_gtv()]
    );
}
```

### Handling the result:

You can receive the result by extending the `on_query_result` function.

```rell
@extend(hcq.on_query_result)
function (id: text, result: hcq.query_result) {
    if (result.error != null) {
        print("Query %s failed: %s".format(id, result.error));
        return;
    }
    
    // Process the result.result (gtv)
    print("Query %s returned: %s".format(id, result.result));
}
```

Alternatively, you can poll for the result using `fetch_query_result`.

```rell
query get_my_query_status(id: text) {
    val result = hcq.fetch_query_result(id);
    if (result == null) return "Pending";
    if (result.error != null) return "Error: " + result.error;
    return "Success: " + result.result.to_text();
}
```
