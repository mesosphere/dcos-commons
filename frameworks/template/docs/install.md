---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20

packageName: template
serviceName: template
---

{% include services/install.md
    techName="TEMPLATE SERVICE"
    packageName=page.packageName
    serviceName=page.serviceName
    minNodeCount="some"
    defaultInstallDescription=" with some master nodes"
    agentRequirements="Each agent node must have some quantity of cpu, memory, disk, and ports."
    serviceAccountInstructionsUrl="https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/"
    enterpriseInstallUrl="https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/" %}
