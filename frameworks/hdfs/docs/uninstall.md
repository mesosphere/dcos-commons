---
post_title: Uninstall
menu_order: 20
feature_maturity: preview
enterprise: 'no'
---

Uninstalling the service is straightforward. Replace `hdfs` with the name of the HDFS instance to be uninstalled.

```
$ dcos package uninstall --app-id=hdfs
```

**Note:** Alternatively, you can [uninstall HDFS from the DC/OS GUI](https://docs.mesosphere.com/1.9/deploying-services/uninstall/).

Then, use the [framework cleaner script](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) to remove your HDFS instance from ZooKeeper and destroy all data associated with it. The script requires several arguments. The default values are:

- `framework_role` is `hdfs-role`.
- `framework_principal` is `hdfs-principal`.
- `zk_path` is `dcos-service-<service-name>`.

These values may vary if you customized them during installation.
