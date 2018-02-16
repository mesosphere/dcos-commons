---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20
---
{% assign data = site.data.services.template %}

{% capture customInstallRequirements %}
- Each agent node must have some quantity of cpu, memory, disk, and ports.
{% endcapture %}

{% capture customInstallConfigurations %}
### Minimal installation

DESCRIBE HOW TO RUN YOUR SERVICE IN THE SMALLEST FEASIBLE SPACE FOR DEMO PURPOSES

### Example custom configuration

A COMMON WAY OF CUSTOMIZING YOUR SERVICE'S CONFIG THAT YOU'D LIKE TO POINT OUT IN DOCUMENTATION
{% endcapture %}

{% include services/install.md
    data=data
    customInstallRequirements=customInstallRequirements
    customInstallConfigurations=customInstallConfigurations %}
