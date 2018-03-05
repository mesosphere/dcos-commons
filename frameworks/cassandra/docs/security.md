---
layout: layout.pug
navigationTitle:
excerpt:
title: Security
menuWeight: 22
---

{% assign data = site.data.services.cassandra %}

# DC/OS {{ data.techName }} Security

The DC/OS {{ data.techName }} service supports {{ data.techName }}'s native transport encryption mechanisms. The service provides automation and orchestration to simplify the usage of these important features. At this time, {{ data.techName }}'s authentication and authorization features are not supported.

*Note*: These security features are only available on DC/OS Enterprise 1.10 and above.

## Transport Encryption

{% include services/security-transport-encryption-lead-in.md data=data %}

{% include services/security-configure-transport-encryption.md data=data %}

*Note*: It is possible to update a running DC/OS {{ data.techName }} service to enable transport encryption after initial installation, but the service may be unavailable during the transition. Additionally, your clients will need to be reconfigured unless `service.security.transport_encryption.allow_plaintext` is set to `true`.

{% include services/security-transport-encryption-clients.md %}
