---
layout: layout.pug
navigationTitle:
excerpt:
title: Security
menuWeight: 22
---

# DC/OS Apache Cassandra Security

The DC/OS Apache Cassandra service supports Apache Cassandra's native transport encryption mechanisms. The service provides automation and orchestration to simplify the usage of these important features. At this time, Apache Cassandra's authentication and authorization features are not supported.

*Note*: These security features are only available on DC/OS Enterprise 1.10 and above.

## Transport Encryption

{% include services/security-transport-encryption-lead-in.md
    techName="Apache Cassandra" plaintext="false" %}

{% include services/security-configure-transport-encryption.md
    techName="Apache Cassandra"
    plaintext="true" %}

*Note*: It is possible to update a running DC/OS Apache Cassandra service to enable transport encryption after initial installation, but the service may be unavilable during the transition. Additionally, your  clients will need to be reconfigured unless `service.security.transport_encryption.allow_plaintext` is set to `true`.

{% include services/security-transport-encryption-clients.md %}
