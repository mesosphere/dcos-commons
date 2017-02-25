## RethinkDB on Mesosphere DC/OS

#### Software Requirements
The following needs to be installed on your local computer
1. Python 3.4 or 2.7 with wheel support
2. Golang 1.7.x+
3. Java SDK
4. DC/OS CLI
    * [Installation Instructions](https://docs.mesosphere.com/1.8/usage/cli/install/)
    * if you are using a cloud-based cluster, you will also need the `awscli` which can be installed by running: `pip install awscli`
4. Gradle (optional)

If you wan to get something running quickly that you can play around with go to the [quickstart](http://example.com) guide. If you have some time and want to know about all of the various features in DC/OS using RethinkDB as a use case, go to the [walkthrough](http://example.com)


TODOs:
* Document `svc.yaml`
* Fix data directories **DONE**
* Start using config file
* Make demo
* Make tests
* Implement other rethinkDB command line subprograms as tasks
* Resource requirements

