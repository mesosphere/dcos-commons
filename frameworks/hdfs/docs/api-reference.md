---
layout: layout.pug
navigationTitle:
excerpt:
title: API Reference
menuWeight: 70

packageName: beta-hdfs
serviceName: hdfs
---

{% include services/api-reference.md
    techName="HDFS"
    packageName=page.packageName
    serviceName=page.serviceName %}

# Connection Information

The HDFS service exposes contents of `hdfs-site.xml` and `core-site.xml` for use by clients. Those contents may be requested as follows (assuming your service is named "{{ page.serviceName }}"):

```bash
$ curl -H "Authorization:token=$auth_token" dcos_url/service/{{ page.serviceName }}/v1/endpoints/hdfs-site.xml
```

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ page.serviceName }}/v1/endpoints/core-site.xml
```

The contents of the responses represent valid `hdfs-site.xml` and `core-site.xml` that can be used by clients to connect to the service.
