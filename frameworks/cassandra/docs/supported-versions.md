---
layout: layout.pug
navigationTitle:
title: Supported Versions
menuWeight: 110
excerpt:
---
{% assign data = site.data.services.cassandra %}

{% include services/supported-versions.md data=data %}
