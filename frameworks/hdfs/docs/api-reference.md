---
layout: layout.pug
navigationTitle:
excerpt:
title: API Reference
menuWeight: 70
---

{% include services/api-reference.md
    techName="HDFS"
    packageName="beta-hdfs"
    serviceName="hdfs" %}

# Connection Information

The HDFS service exposes contents of `hdfs-site.xml` and `core-site.xml` for use by clients. Those contents may be requested as follows:

```bash
$ curl -H "Authorization:token=$auth_token" dcos_url/service/hdfs/v1/endpoints/hdfs-site.xml
```

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/hdfs/v1/endpoints/core-site.xml
```

The contents of the responses represent valid `hdfs-site.xml` and `core-site.xml` that can be used by clients to connect to the service.
