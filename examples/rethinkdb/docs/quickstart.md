### Quickstart (For the impatient)
Below is a smhort guide for someone who wants to get RethinkDB up and running on DC/OS quickly so they have something to play around with. It uses a very plain-vanilla setup and is only meant for small demonstration purposes, for a more in-depth tutorial go to the [walkthrough](http://example.com)
#### Setup
1. Start up a DC/OS cluster using either Vagrant or a cloud provider, for this example it doesn't need to be very large (1-3 agent nodes is plenty)
    * [Cloud provider](https://docs.mesosphere.com/1.8/administration/installing/cloud/) (recommended)
    * [Vagrant](https://docs.mesosphere.com/1.8/administration/installing/local/)
2. Sign into your cluster using the web interface and the CLI
    * `dcos config set <URL_of_dcos_dashboard>`
    * `dcos auth login`

#### Get the code and build
1. Clone the Mesosphere DC/OS SDK repo:
    * `git clone https://github.com/mesosphere/dcos-commons`
2. do `cd examples/rethinkdb`
3. build the framework
    * `./build.sh aws`
    * **n.b.** sometimes Gradle complains here see the [known bugs](http://example.com) section
4. When the build succeeds, there will be three lines with instructions at the bottom of `stdout` that look like this:
```bash
dcos package repo remove rethinkdb-aws
dcos package repo add --index=0 rethinkdb-aws https://<your_S3_bucket>/stub-universe-rethinkdb.zip
dcos package install --yes rethinkdb 
```
Run the commands in the terminal. (the exact output of the second line will vary).

5. It can take around 3-5 minutes for the package to install and to stand up all of the database servers.

#### Use RethinkDB on DC/OS
1. To get to the RethinkDB web UI point your browser at `https://<your_DCOS_cluster_URL>/service/rethinkdb/`
2. SSH to the cluster: `dcos node ssh --master-proxy --leader`
3. Run the python client `docker run -it quay.io/artrand/rethinkdbpythonclient`
    * This is a basic centos7 image that has the rethinkdb python driver and ipython2.7 installed
4. Run `$ ipython` from the master node.
5. Follow the example at [rethinkdb](https://rethinkdb.com/docs/guide/python/) while keeping an eye on the web UI (if you like).
