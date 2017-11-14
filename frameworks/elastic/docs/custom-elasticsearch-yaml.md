---
post_title: Custom Elasticsearch YAML
menu_order: 22
enterprise: 'no'
---

Many Elasticsearch options are exposed via the package configuration in `config.json`, but there may be times when you need to add something custom to the `elasticsearch.yml` file. For instance, if you have written a custom plugin that requires special configuration, you must specify this block of YAML for the Elastic service to use.

Add your custom YAML at install time. In the DC/OS UI, click **Configure**. In the left navigation bar, click `elasticsearch` and find the field for specifying custom elasticsearch YAML. You must base64 encode your block of YAML and enter this string into the field.

You can do this base64 encoding as part of your automated workflow, or you can do it manually with an [online converter](https://www.base64encode.org). 

**Note:** You must only specify configuration options that are not already exposed in `config.json`.
