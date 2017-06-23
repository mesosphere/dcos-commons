# Container StatsD Emitter

A reference implementation of a containerized process which emits StatsD data to an advertised StatsD UDP endpoint.

* Looks for `STATSD_UDP_HOST` and `STATSD_UDP_PORT` in the environment, pointing to where metrics should be sent. These environment variables are automatically provided by Mesos on DC/OS EE clusters 1.7+.
* A `-debug` option enables additional logs to stdout.

## Prerequisites

```bash
apt-get install golang-go
```

## Build

```bash
$ go build
```

## Run locally

```bash
$ ./statsd-emitter -h
$ STATSD_UDP_HOST="127.0.0.1" STATSD_UDP_PORT="8125" ./statsd-emitter -debug
```

## Run in Marathon

Create the following application (in JSON Mode):

```json
{
  "id": "statsd-emitter",
  "cmd": "./statsd-emitter",
  "cpus": 1,
  "mem": 128,
  "disk": 0,
  "instances": 1,
  "uris": [
    "https://s3-us-west-2.amazonaws.com/nick-dev/statsd-emitter.tgz"
  ]
}
```
