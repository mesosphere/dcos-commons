# Hello World service

This service is used to test and demonstrate SDK features. It doesn't do much on its own.

For more information about service development, see the [SDK docs site](https://mesosphere.github.io/dcos-commons/).

## Sample configuration files

The default example [svc.yml](src/main/dist/svc.yml) has two pods, with volumes and is used in the 'official' `hello-world` package.

[Multiple example configuration files](src/main/dist/examples/) are also provided (change the name of the default YAML file at ["universe/config.json"](universe/config.json)). In particular:

 - [simple.yml](src/main/dist/simple.yml): Bare minimum example. Just one pod, with no extra features.
 - [plan.yml](src/main/dist/plan.yml): Two pods, with healthcheck, volumes, ports, and with a plan structure.
 - [uri.yml](src/main/dist/uri.yml): Sample pod and task-specific URIs that are downloaded before task launch.


See [marathon.json.mustache](universe/marathon.json.mustache) for more information:
```
[...]
    "DCOS_MIGRATION_API_PATH": "/v1/plan",
    "MARATHON_SINGLE_INSTANCE_APP":"true",
    "DCOS_SERVICE_NAME": "{{service.name}}",
    "DCOS_SERVICE_PORT_INDEX": "0",
    "DCOS_SERVICE_SCHEME": "http"
[...]
  "portDefinitions": [
    {
      "port": 0,
      "protocol": "tcp",
      "name": "api"
    }
[...]
```

