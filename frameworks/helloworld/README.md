Refer to the [top-level tutorial document](../../docs/pages/tutorial.md).

# Sample configuration files

The default example [svc.yml](src/main/dist/svc.yml) has two pods, with volumes and is used in the 'official' `hello-world` package.

[Multiple example configuration files](src/main/dist/examples/) are also provided (change the name of the default YAML file at ["universe/config.json"](universe/config.json)). In particular:

 - [simple.yml](src/main/dist/examples/simple.yml): Bare minimum example. Just one pod, with no extra features.
 - [plan.yml](src/main/dist/examples/plan.yml): Two pods, with healthcheck, volumes, ports, and with a plan structure.
 - [uri.yml](src/main/dist/examples/uri.yml): Sample pod and task-specific URIs that are downloaded before task launch.

# Framework API Port

Please note that Marathon dynamically selects a port number, and passes this information to the framework (`PORT0` in our examples). We start the API service on that given port number. You can start the framework by giving a specific port number, if you are sure that the port is available to you. This is very unlikely, so the api-port should be set to a variable as shown below:
```
    api-port : {{PORT0}}
```

See [marathon.json.mustache](universe/marathon.json.mustache) for more information:
```
[...]
    "DCOS_MIGRATION_API_PATH": "/v1/plan",
    "MARATHON_SINGLE_INSTANCE_APP":"true",
    "DCOS_SERVICE_NAME": "{{service.name}}",
    "DCOS_SERVICE_PORT_INDEX": "0",
    "DCOS_SERVICE_SCHEME": "http”
[...]
  "portDefinitions": [
    {
      "port": 0,
      "protocol": "tcp",
      "name": "api",
      "labels": { "VIP_0": "/api.{{service.name}}:80" }
    }
[...]
```

