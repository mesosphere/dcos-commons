---
layout: layout.pug
navigationTitle: 
excerpt:
title: API Reference
menuWeight: 70

---

The DC/OS Apache Cassandra Service implements a REST API that may be accessed from outside the cluster. The <dcos_url> parameter referenced below indicates the base URL of the DC/OS cluster on which the Apache Cassandra Service is deployed.

<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the Apache Cassandra REST API directly. You do not need the token to access the Apache Cassandra nodes themselves.

If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.9/security/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your HTTP API token to the DC/OS endpoint]https://dcos.io/docs/1.9/security/iam-api/).

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```
$ export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.9/networking/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.9/networking/tls-ssl/), do not use the `--ca-cert` flag.

# Plan API
The Plan API provides endpoints for monitoring and controlling service installation and configuration updates.

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/plans/deploy
```
## Pause Installation

The installation will pause after completing installation of the current node and wait for user input.

```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/plans/deploy/interrupt
```

## Resume Installation

The REST API request below will resume installation at the next pending node.

```bash
$ curl -X PUT <dcos_surl>/service/cassandra/v1/plans/deploy/continue
```

# Nodes API

The pod API provides endpoints for retrieving information about nodes, restarting them, and replacing them.

## List Nodes

A list of available node ids can be retrieved by sending a GET request to `/v1/pod`:

CLI Example
```
$ dcos cassandra pod list
```

HTTP Example
```
$ curl  -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/pod
```

## Node Info

You can retrieve node information by sending a GET request to `/v1/pod/<node-id>/info`:

```
$ curl  -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/pod/<node-id>/info
```

CLI Example
```
$ dcos cassandra pod info journalnode-0
```

HTTP Example
```
$ curl  -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/pod/journalnode-0/info

```

## Replace a Node

The replace endpoint can be used to replace a node with an instance running on another agent node.

CLI Example
```
$ dcos cassandra pod replace <node-id>
```

HTTP Example
```
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/pod/<node-id>/replace
```

If the operation succeeds, a `200 OK` is returned.

## Restart a Node

The restart endpoint can be used to restart a node in place on the same agent node.

CLI Example
```
$ dcos cassandra pod restart <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/pod/<node-id>/restart
```

If the operation succeeds a `200 OK` is returned.

## Pause a Node

The pause endpoint can be used to relaunch a node in an idle command state for debugging purposes.

CLI example
```
dcos beta-cassandra debug pod pause <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/pod/<node-id>/pause
```

# Configuration API

The configuration API provides an endpoint to view current and previous configurations of the cluster.

## View Target Config

You can view the current target configuration by sending a GET request to `/v1/configurations/target`.

CLI Example
```
$ dcos cassandra config target
```

HTTP Example
```
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/configurations/target
```

## List Configs

You can list all configuration IDs by sending a GET request to `/v1/configurations`.

CLI Example
```
$ dcos cassandra config list
```

HTTP Example
```
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/configurations
```

## View Specified Config

You can view a specific configuration by sending a GET request to `/v1/configurations/<config-id>`.

CLI Example
```
$ dcos cassandra config show 9a8d4308-ab9d-4121-b460-696ec3368ad6
```

HTTP Example
```
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/cassandra/v1/configurations/9a8d4308-ab9d-4121-b460-696ec3368ad6
```

# Service Status Info

Send a GET request to the `/v1/state/properties/suppressed` endpoint to learn if Apache Cassandra is in a `suppressed` state and not receiving offers. If a service does not need offers, Mesos can "suppress" it so that other services are not starved for resources.

You can use this request to troubleshoot: if you think Apache Cassandra should be receiving resource offers, but is not, you can use this API call to see if Apache Cassandra is suppressed.
```
curl -H "Authorization: token=$auth_token" "<dcos_url>/service/cassandra/v1/state/properties/suppressed"
