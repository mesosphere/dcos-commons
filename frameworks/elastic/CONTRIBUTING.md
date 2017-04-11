The Elastic framework is tied to a particular version of the Elastic stack. Changing the version requires a few steps:

1. Make a new version of the [statsd plugin](https://github.com/Automattic/elasticsearch-statsd-plugin) if it's not 
already up-to-date. Look at prior version bump pull requests to see what needs changing. Do not proceed until your 
PR has been merged.
1. Change the `ELASTIC_VERSION` in marathon.json.mustache.
1. Update the `SUPPORT_DIAGNOSTICS_VERSION` in marathon.json.mustache if a [new version available](https://github.com/elastic/elasticsearch-support-diagnostics/releases).
1. Create a PR on `dcos-commons` and ensure all integration tests pass. Do not proceed until your PR has been merged.
1. Create a tag of the form `ELASTIC_1.x.y-5.a.b` and push it to master.
1. Edit release notes for the tag in GitHub, tying the release to a particular `dcos-commons` SDK and linking out 
to the Elastic release notes.
 
Once that process is completed, you can begin the process of creating a new Elastic package for Universe.
