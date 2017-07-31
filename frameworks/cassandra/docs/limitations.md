---
post_title: Limitations
menu_order: 80
feature_maturity: preview
enterprise: 'no'
---

## Rack Aware Placement

The DC/OS Cassandra Service does not currently leverage rack information for placement decisions.  However, placement constraints can be used to manually place instances across racks if desired.   

## Security

The security features  are not supported at this time.

## Overlay networks

When Cassandra is deployed on a virtual network, the configuration cannot be updated after installation.

## Data Center Name

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