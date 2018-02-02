---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---

{% include services/install.md
    tech_name="TEMPLATE SERVICE"
    package_name="beta-template"
    service_name="template"
    min_node_count="some"
    default_install_description=" with some master nodes"
    agent_requirements="Each agent node must have some quantity of cpu, memory, disk, and ports."
    service_account_instructions_url="https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/"
    enterprise_install_url="https://docs.mesosphere.com/latest/security/service-auth/custom-service-auth/" %}
