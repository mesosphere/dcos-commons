Refer to the [top-level tutorial document](../../docs/tutorial.md).

Three sample configuration files are given here (change the name of the input yml file at "universe/marathon.json.mustache"):
       - svc.yml  (only one pod)
       - svc_simple.yml (two pods, with disk)
       - svc_plan.yml (two pods, with healthcheck, with disk, with port, and with a plan structure)


Framework API Port:

	Please note that Marathon dynamically selects a port number, and passes this information to the framework ( PORT0 in our examples). We start the API service on that given port number. You can start the framework by giving a specific port number, if you are sure that the port is available to you. This is very unlikely, so api-port should be set to a variable as shown below:  
   
		api-port : {{PORT0}}

See universe/marathon.json.mustache for more information:
…
…
 "DCOS_MIGRATION_API_PATH": "/v1/plan",
    "MARATHON_SINGLE_INSTANCE_APP":"true",
    "DCOS_SERVICE_NAME": "{{service.name}}",
    "DCOS_SERVICE_PORT_INDEX": "0",
    "DCOS_SERVICE_SCHEME": "http”
…
…
…
  "portDefinitions": [
    {
      "port": 0,
      "protocol": "tcp",
      "name": "api",
…
…
…




