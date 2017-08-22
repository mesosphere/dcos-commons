# keystore-app

The `keystore-app` is simplified version of `dropwizard-example` application
that comes with a webserver that serves requests for `/hello-world` path
and a custom CLI command `truststoretest` that uses the `dropwizard-client`
which issues a `GET` request to a provided HTTP(s) URL.

The [Dropwizard](http://www.dropwizard.io) library exposes a flexible
[SSL configuration](http://www.dropwizard.io/1.1.2/docs/manual/core.html#ssl)
options for both [server](http://www.dropwizard.io/1.1.2/docs/manual/configuration.html#https)
and [HTTP client](http://www.dropwizard.io/1.1.2/docs/manual/configuration.html#tls) components.

## Configuration

The application comes with example configurations stored in `./config`
directory:

- `local.yml`: launch the `server` and run the `truststoretest` CLI command
  locally

- `integration-test.yml`: can be used in SDK launched container task that
  requests for TLS certificate with name `dropwizard` that are exposed as a
  keystore and trustore files.
  
Local version requires `config/local.keystore` and `config/local.truststore` files.
The both JKS files must be protected with `notsecure` password. To see the details
about files content please see SDK developer guide. 

## server

The `server` command will start an HTTP server that listens on ports `8080` and
an HTTPS server on port `8443`.

To start a webserver build the `shadeJar` application and start it with
configuration file path.

```
./gradlew shadowJar
java -jar build/libs/keystore-app-${VERSION}-all.jar server config/local.yml
```

To test the application with curl:

```
curl -k https://localhost:${PORT}/hello-world
```

## truststoretest

The `truststoretest` command issues a `GET` request to a provided URL with
custom `HttpClient` configuration that is read from YAML file. Configure
the `jerseyClient` section to modify client settings.

The `local.yml` and `integration-test.yml` files configure the `HttpClient`
to use a custom `trustStore` which limits which TLS connection will be verified.

## Build

To build a application for local testing run:

```
./gradlew :keystore-app:shadowJar
```

which produces single jar in `./build/libs/keystore-app-${VERSION}-all.jar`.

For building an artifact that is packaged with `integration-test.yml` file
that can be used to run SDK TLS integration tests run:

```
./gradlew :keystore-app:integrationTestZip
```

The task builds a ZIP file contianing `shadowJar` package and the
`integration-test.yml` in `distributions` directory. The artifact can be
downloaded by `mesos-fetcher` to a container with `java` runtime.

## Integration tests

The example `hello-world` application automatically builds `keystore-app` and uploads
the `zip` file to `S3` during the each integration test run.

