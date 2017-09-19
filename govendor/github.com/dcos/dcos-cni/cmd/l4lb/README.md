# A CNI plugin for Minuteman and Spartan
Minuteman and Spartan are the internal distributed load balancer and
the distributed DNS service for DC/OS. This CNI plugin allows
containers running in an isolated virtual network, which don't allow
direct communication to the underlying host network, to get access to
services provided by Minuteman and Spartan.

You can get more details about the design of the plugin in
this [design doc](https://goo.gl/xBUc71).

# Usage
The CNI plugin is designed to work with any 3rd party network plugin. Below we give an example of using the `dcos-l4lb` plugin with a bridge CNI plugin:

```
 "cniVersion": "0.2.0",
 "name": "spartan-net",
 "type": "dcos-l4lb",
 "delegate" : {
        "type" : "bridge",
        "bridge": "sprt-cni0",
        "ipMasq": true,
        "isGateway": true,   
        "ipam": {
          "type": "host-local",
          "subnet": "192.168.30.0/24",
          "routes": [
            { "dst": "10.0.0.0/8" }
          ]
        }
}
```
In the above example the `delegate` clause informs the `dcos-l4lb` plugin to invoke the `bridge` plugin with its respective parameters. During CNI ADD the `dcos-l4lb` plugin will first invoke the `bridge` plugin, with the config specified in `delegate`. On successful execution of the bridge plugin it will attach the container network namespace to the spartan network, and will also register the container's network namespace with minuteman. Attaching the container to the spartan network will allow the container to route all DNS queries to spartan, and registering network namespace with minuteman will allow minuteman to insert IPVS enteries into the container's network namespace for load-balancing.

During CNI DEL the `dcos-l4lb` will first detach the container network namespace from the spartan network. It will then `de-register` the network namespace from minuteman. Finally it will invoke DEL on the bridge plugin.

While invoking CNI ADD or DEL on the bridge plugin the `dcos-l4lb` plugin will copy the `cniVersion`, `name` and `args` parameters specified in its own CNI configuration to the CNI configuration of the `bridge` plugin specified in the `delegate` field.

**NOTE:** While this example specifically deals with the CNI bridge plugin, we could potentially use any other CNI plugin instead of the bridge pluging to provide IP connectivity to the container. Just replace the CNI configuration of bridge plugin with the configuration of the desired plugin in the `delegate` field. 

# Parameters
By default `Spartan` and `Minuteman` features are enabled in the `dcos-l4lb` plugin. However we give the user the flexibility of turning of `Spartan` or `Minuteman` (but not both) features of the plugin. These are the extra parameters that can be specified in the CNI configuration for the plugin

* `spartan` (true|false): A boolean field that tells the `dcos-l4lb` plugin whether it should attach the container to the spartan network or not. Default is `true`.
* `minuteman`: A dictionary field that takes the following values;
  * `enable`: Enable the minuteman feature.
  * `path`: The directory where the `dcos-l4lb` will checkpoint the container ID and the `netns` associated with the container for  minuteman to learn about containers that need L4LB access.
