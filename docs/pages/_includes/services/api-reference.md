The DC/OS {{ include.data.techName }} Service implements a REST API that may be accessed from outside the cluster. The <dcos_url> parameter referenced below indicates the base URL of the DC/OS cluster on which the {{ include.data.techName }} Service is deployed.

# REST API Authentication

REST API requests must be authenticated. This authentication is only applicable for interacting with the {{ include.data.techName }} REST API directly. You do not need the token to access the {{ include.data.techName }} nodes themselves.

If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/latest/security/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your HTTP API token to the DC/OS endpoint]https://dcos.io/docs/latest/security/iam-api/).

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```bash
$ export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/latest/networking/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/latest/networking/tls-ssl/), do not use the `--ca-cert` flag.

# Plan API

The Plan API provides endpoints for monitoring and controlling service installation and configuration updates.

## List plans

You may list the configured plans for the service. By default, all services at least have a `deploy` plan and a `recovery` plan. Some services may have additional custom plans defined.

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/plans
```

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} plan list
```

## View plan

You may view the current state of a listed plan:

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/plans/<plan>
```

The CLI may be used to show a formatted tree of the plan (default), or the underlying JSON data as retrieved from the above HTTP endpoint:

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} plan show <plan>
```

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} plan show <plan> --json
```

## Pause plan

The installation will pause after completing the operation of the current node and wait for user input before proceeding further.

```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/plans/deploy/interrupt
```

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} plan pause deploy
```

## Resume plan

The REST API request below will resume the operation at the next pending node.

```bash
$ curl -X PUT -H "Authorization:token=$auth_token" <dcos_surl>/service/{{ include.data.serviceName }}/v1/plans/deploy/continue
```

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} plan continue deploy
```

# Nodes API

The pod API provides endpoints for retrieving information about nodes, restarting them, and replacing them.

## List Nodes

A list of available node ids can be retrieved by sending a GET request to `/v1/pod`:

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod list
```

HTTP Example
```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/pod
```

## Node Info

You can retrieve node information by sending a GET request to `/v1/pod/<node-id>/info`:

```bash
$ curl  -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/pod/<node-id>/info
```

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod info journalnode-0
```

HTTP Example
```bash
$ curl  -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/pod/journalnode-0/info
```

## Replace a Node

The replace endpoint can be used to replace a node with an instance running on another agent node.

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod replace <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/pod/<node-id>/replace
```

If the operation succeeds, a `200 OK` is returned.

## Restart a Node

The restart endpoint can be used to restart a node in place on the same agent node.

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} pod restart <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/pod/<node-id>/restart
```

If the operation succeeds a `200 OK` is returned.

## Pause a Node

The pause endpoint can be used to relaunch a node in an idle command state for debugging purposes.

CLI example
```bash
dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} debug pod pause <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/pod/<node-id>/pause
```

# Configuration API

The configuration API provides an endpoint to view current and previous configurations of the cluster.

## View Target Config

You can view the current target configuration by sending a GET request to `/v1/configurations/target`.

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} config target
```

HTTP Example
```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/configurations/target
```

## List Configs

You can list all configuration IDs by sending a GET request to `/v1/configurations`.

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} config list
[
  "319ebe89-42e2-40e2-9169-8568e2421023",
  "294235f2-8504-4194-b43d-664443f2132b"
]
```

HTTP Example
```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/configurations
[
  "319ebe89-42e2-40e2-9169-8568e2421023",
  "294235f2-8504-4194-b43d-664443f2132b"
]
```

## View Specified Config

You can view a specific configuration by sending a GET request to `/v1/configurations/<config-id>`.

CLI Example
```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName }} config show 9a8d4308-ab9d-4121-b460-696ec3368ad6
```

HTTP Example
```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ include.data.serviceName }}/v1/configurations/9a8d4308-ab9d-4121-b460-696ec3368ad6
```
