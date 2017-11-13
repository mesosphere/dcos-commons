---
post_title: Custom elasticsearch.yml
menu_order: 22
enterprise: 'no'
---

# Custom YAML

Many Elasticsearch options are exposed via the package configuration in `config.json`, but there may be times when 
you need to add something custom to the `elasticsearch.yml` file. For instance, if you have written a custom plugin
that requires special configuration, you will need to specify this block of YAML for the Elastic service to use.

When installing the Elastic package using the DC/OS UI, click "Configure". In the left navigation bar, 
click `elasticsearch` and notice the field for specifying custom elasticsearch YAML. You must base64 encode your block
of YAML and enter this string into the field.

You can do this base64 encoding as part of your automated workflow, or you can do it manually with an 
[online converter](https://www.base64encode.org). 

Note that you must only specify configuration options that are not exposed already in `config.json`.
