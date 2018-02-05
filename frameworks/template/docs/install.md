---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---

{% include services/install.md
    techName="TEMPLATE SERVICE"
    packageName="beta-template"
    serviceName="template"
    minNodeCount="some"
    defaultInstallDescription=" with some master nodes"
    agentRequirements="Each agent node must have some quantity of cpu, memory, disk, and ports."
    serviceAccountInstructionsUrl="https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/"
    enterpriseInstallUrl="https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/" %}
