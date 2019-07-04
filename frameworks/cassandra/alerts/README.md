# Cassandra alerts for DC/OS

In order to setup alerts on your cluster, you must install the following two packages:
- `dcos-monitoring`
- `cassandra`

1. Save the following to an `options.json` file:
```
{
  "prometheus": {
    "alert_rules_repository": {
      "url": "https://github.com/dcos/prometheus-alert-rules",
      "path": "/rules/cassandra/"
    }
  }
}
``` 
2. Install package:
```
dcos package install beta-dcos-monitoring --options=options.json
```
3. After deployment is complete, check Prometheus is running by going to the following URL: 
```
http://<cluster-ip>/service/dcos-monitoring/prometheus
```
4. In Prometheus UI, click on the "Alert" button to check Alerts were successfully imported
