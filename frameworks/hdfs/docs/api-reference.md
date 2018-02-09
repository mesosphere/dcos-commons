---
layout: layout.pug
navigationTitle:
excerpt:
title: API Reference
menuWeight: 70
---
{% assign data = site.data.services.hdfs %}

{% include services/api-reference.md data=data %}

# Connection Information

The HDFS service exposes contents of `hdfs-site.xml` and `core-site.xml` for use by clients. Those contents may be requested as follows (assuming your service is named "{{ data.serviceName }}"):

```bash
$ curl -H "Authorization:token=$auth_token" dcos_url/service/{{ data.serviceName }}/v1/endpoints/hdfs-site.xml
```

```bash
$ curl -H "Authorization:token=$auth_token" <dcos_url>/service/{{ data.serviceName }}/v1/endpoints/core-site.xml
```

The contents of the responses represent valid `hdfs-site.xml` and `core-site.xml` that can be used by clients to connect to the service.
