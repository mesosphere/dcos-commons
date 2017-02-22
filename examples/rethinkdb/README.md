## RethinkDB on Mesosphere DC/OS

#### Software Requirements
The following needs to be installed on your workstation/local computer
1. Python 3.4 or 2.7 with wheel support
2. Golang 1.7.x+
3. Java SDK
4. DC/OS CLI
    * [Installation Instructions](http://example.com)
    * if you are using a cloud-based cluster, you will also need the `awscli` which can be installed by running: `pip install awscli`
4. Gradle (optional)

### For the impatient
#### Setup
1. Start up a DC/OS cluster using either Vagrant or a cloud provider, for this example it doesn't need to be very large (1-3 agent nodes is plenty)
    * [Cloud provider](http://example.com) (recommended)
    * [Vagrant](http://exampple.com)
2. Sign into your cluster using the web interface and the CLI
    * `dcos config set <URL_of_dcos_dashboard>`
    * `dcos auth login`
#### Get the code
1. Clone the Mesosphere DC/OS SDK repo:
    * `git clone `