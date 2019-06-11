# Cassandra alerts for DC/OS

In order to setup alerts on your cluster, you must install the following two packages:
- `dcos-monitoring`
- `cassandra`

1. Install required package: `dcos package install beta-dcos-monitoring` with below configuration in prometheus tab : 


     under `Alert Rules Repository`:
     
    `Url : https://github.com/mesosphere/dcos-commons/`
    
    `Path : /frameworks/cassandra/alerts/`
    
    `Reference Name : refs/heads/DCOS-52832-Add-alerts-dcos-monitoring`

2. After deployment is complete, check Prometheus is running by going to the following URL: `http://<cluster-ip>/service/dcos-monitoring/prometheus`

    `http://<cluster-ip>/service/dcos-monitoring/prometheus`

3. In Prometheus UI, click on the "Alert" button to check Alerts were successfully imported
