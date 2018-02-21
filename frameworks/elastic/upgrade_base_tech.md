The Elastic framework is tied to a particular version of the Elastic stack. Changing the version requires a few steps:

1. Make a new version of the [statsd plugin](https://github.com/mesosphere/elasticsearch-statsd-plugin). Look at prior version bump pull requests to see what needs changing.
1. Issue an upstream PR.
1. Publish a release and attach the plugin ZIP file artifact created by `mvn package -Dtests.security.manager=false`.
1. In the `dcos-commons` repo, change the `TEMPLATE_ELASTIC_VERSION` in `versions.sh`.
1. In the `dcos-commons` repo, update the `TEMPLATE_SUPPORT_DIAGNOSTICS_VERSION` in `versions.sh` if a [new version is available](https://github.com/elastic/elasticsearch-support-diagnostics/releases).
1. Create a PR on `dcos-commons` and ensure all integration tests pass. 
