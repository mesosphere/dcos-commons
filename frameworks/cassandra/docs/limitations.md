---
post_title: Limitations
nav_title: Limitations
menu_order: 110
post_excerpt: ""
feature_maturity: preview
enterprise: 'no'
---

- For multi-data-center configurations, the hostnames for the seed nodes in each cluster must be routable from every other cluster. Typically, DC/OS hosts are members of a private subnet that is not routable from external hosts, so further network configuration is required to achieve this.
