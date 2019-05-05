# Cassandra dashboards for DC/OS

This is a folder for Grafana dashboards. In order to install a dashboard on a cluster, `dcos-monitoring` package need to be installed (as well as `cassandra` package itself:

1. Install required package: `dcos package install beta-dcos-monitoring`
2. After deploy is complete, please check, that Gradana and Prometheus are running:

    `http://<cluster-ip>/service/dcos-monitoring/grafana`

    `http://<cluster-ip>/service/dcos-monitoring/prometheus`

3. In Prometheus UI, click on a "Graph" button, and in the "Expression" field, enter "org_apache_cassandra_" and check that Cassandra metrics are collected into Prometheus.

4. To import a dashboard, go to Grafana UI and in the left toolbar click on a plus sign - "Import". Click on a "Upload .json file" button, choose a json file according to DC/OS version and click "Import".
   
5. Dashboard will be opened and you can see charts and metrics.