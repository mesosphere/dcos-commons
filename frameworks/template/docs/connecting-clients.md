---
post_title: Connecting Clients
menu_order: 50
post_excerpt: ""
enterprise: 'no'
---

# Connecting Clients
One of the benefits of running containerized services is that they can be placed anywhere in the cluster. Because they can be deployed anywhere on the cluster, clients need a way to find the service. This is where service discovery comes in.

<a name="discovering-endpoints"></a>
## Discovering Endpoints

Once the service is running, you may view information about its endpoints via either of the following methods:
- CLI:
  - List endpoint types: `dcos _PKGNAME_ endpoints`
  - View endpoints for an endpoint type: `dcos _PKGNAME_ endpoints <endpoint>`
- API:
  - List endpoint types: `<dcos-url>/service/_PKGNAME_/v1/endpoints`
  - View endpoints for an endpoint type: `<dcos-url>/service/_PKGNAME_/v1/endpoints/<endpoint>`

Returned endpoints will include the following:
- `.autoip.dcos.thisdcos.directory` hostnames for each instance that will follow them if they're moved within the DC/OS cluster.
- A HA-enabled VIP hostname for accessing any of the instances (optional).
- A direct IP address for accessing the service if `.autoip.dcos.thisdcos.directory` hostnames are not resolvable.
- If your service is on a virtual network such as the `dcos` overlay network, then the IP will be from the subnet allocated to the host that the task is running on. It will not be the host IP. To resolve the host IP use Mesos DNS (`<task>.<service>.mesos`).

In general, the `.autoip.dcos.thisdcos.directory` endpoints will only work from within the same DC/OS cluster. From outside the cluster you can either use direct IPs or set up a proxy service that acts as a frontend to your _SERVICENAME_ instance. For development and testing purposes, you can use [DC/OS Tunnel](https://docs.mesosphere.com/latest/administration/access-node/tunnel/) to access services from outside the cluster, but this option is not suitable for production use.

<a name="connecting-clients-to-endpoints"></a>
## Connecting Clients to Endpoints

_GIVEN A RELEVANT EXAMPLE CLIENT FOR YOUR SERVICE, PROVIDE INSTRUCTIONS FOR CONNECTING THAT CLIENT USING THE ENDPOINTS LISTED ABOVE. WE RECOMMEND USING THE .MESOS ENDPOINTS IN YOUR EXAMPLE AS THEY WILL FOLLOW TASKS IF THEY ARE MOVED WITHIN THE CLUSTER._
