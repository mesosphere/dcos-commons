---
post_title: API Reference
menu_order: 70
enterprise: 'no'
---

<!-- {% raw %} disable mustache templating in this file: retain templated examples as-is -->

The DC/OS _SERVICENAME_ Service implements a REST API that may be accessed from outside the cluster. The <dcos_url> parameter referenced below indicates the base URL of the DC/OS cluster on which the DC/OS _SERVICENAME_ Service is deployed.

<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the DC/OS _SERVICENAME_ REST API directly. You do not need the token to access the _SERVICENAME_ nodes themselves.

If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.9/security/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.9/security/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your HTTP API token to the DC/OS endpoint](https://dcos.io/docs/latest/security/iam-api/).

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```shell
export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.9/networking/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.9/networking/tls-ssl/), do not use the `--ca-cert` flag.

# Plan API
The Plan API provides endpoints for monitoring and controlling service installation and configuration updates.

```shell
curl -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/plans/deploy
```

## Pause Installation

The installation will pause after completing installation of the current node and wait for user input.

```shell
curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/plans/deploy/interrupt
```

## Resume Installation

The REST API request below will resume installation at the next pending node.

```shell
curl -X PUT <dcos_surl>/service/_PKGNAME_/v1/plans/deploy/continue
```

# Connection API

```shell
curl -H "Authorization:token=$auth_token" dcos_url/service/_PKGNAME_/v1/endpoints/<endpoint>
```

You will see a response similar to the following:

<!-- TODO: provide endpoint <endpoint> example (default options) output -->

The contents of the endpoint response contain details sufficient for clients to connect to the service.

# Nodes API

The pod API provides endpoints for retrieving information about nodes, restarting them, and replacing them.

## List Nodes

A list of available node ids can be retrieved by sending a GET request to `/v1/pod`:

CLI Example

```shell
dcos _PKGNAME_ pod list
```

HTTP Example

```shell
curl  -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/pod
```

You will see a response similar to the following:

<!-- TODO: provide pod list example (default options) output -->

## Node Info

You can retrieve node information by sending a GET request to `/v1/pod/<node-id>/info`:

```shell
curl  -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/pod/<node-id>/info
```

You will see a response similar to the following:

<!-- TODO: using node-0 here, but ensure that the node name matches a _SERVICENAME_ service node type -->

CLI Example

```shell
dcos _PKGNAME_ pod info node-0
```

HTTP Example

```shell
curl  -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/pod/node-0/info
```

You will see a response similar to the following:

<!-- TODO: provide pod <node-id> example (default options) output -->

## Replace a Node

The replace endpoint can be used to replace a node with an instance running on another agent node.

CLI Example

```shell
dcos _PKGNAME_ pod replace <node-id>
```

HTTP Example

```shell
curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/pod/<node-id>/replace
```

If the operation succeeds, a `200 OK` is returned.

## Restart a Node

The restart endpoint can be used to restart a node in place on the same agent node.

CLI Example

```shell
dcos _PKGNAME_ pod restart <node-id>
```

HTTP Example

```shell
curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/pod/<node-id>/restart
```

If the operation succeeds a `200 OK` is returned.

# Configuration API

The configuration API provides an endpoint to view current and previous configurations of the cluster.

## View Target Config

You can view the current target configuration by sending a GET request to `/v1/configurations/target`.

CLI Example

```shell
dcos _PKGNAME_ config target
```

HTTP Example

```shell
curl -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/configurations/target
```

You will see a response similar to the following:

<!-- TODO: provide configurations/target example (default options) output -->

## List Configs

You can list all configuration IDs by sending a GET request to `/v1/configurations`.

CLI Example

```shell
dcos _PKGNAME_ config list
```

HTTP Example

```shell
curl -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/configurations
```

You will see a response similar to the following:

<!-- TODO: provide configurations example (default options) output -->

## View Specified Config

You can view a specific configuration by sending a GET request to `/v1/configurations/<config-id>`.

CLI Example

```shell
dcos _PKGNAME_ config show 9a8d4308-ab9d-4121-b460-696ec3368ad6
```

HTTP Example

```shell
curl -H "Authorization:token=$auth_token" <dcos_url>/service/_PKGNAME_/v1/configurations/9a8d4308-ab9d-4121-b460-696ec3368ad6
```

You will see a response similar to the target config above.

# Service Status Info
Send a GET request to the `/v1/state/properties/suppressed` endpoint to learn if DC/OS _SERVICENAME_ is in a `suppressed` state and not receiving offers. If a service does not need offers, Mesos can "suppress" it so that other services are not starved for resources.
You can use this request to troubleshoot: if you think DC/OS _SERVICENAME_ should be receiving resource offers, but is not, you can use this API call to see if DC/OS _SERVICENAME_ is suppressed.

```shell
curl -H "Authorization: token=$auth_token" "<dcos_url>/service/_PKGNAME_/v1/state/properties/suppressed"
```
