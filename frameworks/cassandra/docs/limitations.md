---
post_title: Limitations
menu_order: 80
feature_maturity: preview
enterprise: 'no'
---

* Data Center Name

Data Center Name:

The name of the data center cannot be changed after Cassandra installation. `service.data_center` and `service.rack` options are not allowed to be modified once Cassandra is installed. 

```
"service": {
……
      data_center": {
          "description": "The name of the data center this cluster is running in",
          "type": "string",
          "default": "datacenter1"
        },
        "rack": {
          "description": "The name of the rack this cluster is running on",
          "type": "string",
          "default": "rack1"
        },
……
}
```
