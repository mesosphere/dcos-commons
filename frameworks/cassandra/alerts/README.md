# Cassandra dashboards for DC/OS

This is a folder for Alerts. In order to setup aleert on a cluster, `dcos-monitoring` package need to be installed as well as `cassandra` package itself:

1. Install required package: `dcos package install beta-dcos-monitoring` with below configuration in prometheus tab : 


     Alert Rules Repository
    `Url : https://github.com/mesosphere/dcos-commons/`
    `Path : /frameworks/cassandra/alerts/`
    `Reference Name : refs/heads/DCOS-52832-Add-alerts-dcos-monitoring`

2. After deploy is complete, please check, that Prometheus is running:

    `http://<cluster-ip>/service/dcos-monitoring/prometheus`

3. In Prometheus UI, click on a "Alert" button to check Alerts.
