---
layout: layout.pug
navigationTitle:
excerpt:
title: Quick Start
menuWeight: 40
---
{% assign data = site.data.services.template %}

## Prerequisites

- [DC/OS installed on your cluster](https://docs.mesosphere.com/latest/administration/installing/).

## Steps

1. If you are using open source DC/OS, install {{ data.techName }} cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for information.

    ```bash
    dcos package install {{ data.packageName }}
    ```

    Alternatively, you can install {{ data.techName }} from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

1. The service will now deploy with a default configuration. You can monitor its deployment via the Services tab of the DC/OS web interface.

1. Connect a client to {{ data.techName }}.
    ```bash
    dcos {{ data.packageName }} --name={{ data.serviceName }} endpoints
    [
        "_LIST_",
        "_OF_",
        "_ENDPOINTS_"
    ]

    dcos {{ data.packageName }} --name={{ data.serviceName }} endpoints _ENDPOINT_
    {
        "address": ["10.0.3.156:_PORT_", "10.0.3.84:_PORT_"],
        "dns": ["_POD_-0.{{ data.serviceName }}.mesos:_PORT_", "_POD_-1.{{ data.serviceName }}.mesos:_PORT_"]
    }
    ```

1. _PROVIDE A SIMPLE EXAMPLE OF HOW TO CONNECT A CLIENT AND INTERACT WITH YOUR PRODUCT (E.G., WRITE DATA, READ DATA)._

## See Also

- [Connecting clients](https://docs.mesosphere.com/service-docs/{{ data.packageName }}/connecting-clients/)
