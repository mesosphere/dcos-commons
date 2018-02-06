---
layout: layout.pug
navigationTitle:
excerpt:
title: Quick Start
menuWeight: 40

packageNamePretty: YOUR_SERVICE
packageName: template
serviceName: template
---

## Prerequisites

- [DC/OS installed on your cluster](https://docs.mesosphere.com/latest/administration/installing/).

## Steps

1. If you are using open source DC/OS, install {{ page.packageNamePretty }} cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for information.

    ```bash
    dcos package install {{ page.packageName }}
    ```

    Alternatively, you can install {{ page.packageNamePretty }} from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

1. The service will now deploy with a default configuration. You can monitor its deployment via the Services tab of the DC/OS web interface.

1. Connect a client to {{ page.packageNamePretty }}.
    ```bash
    dcos {{ page.packageName }} --name={{ page.serviceName }} endpoints
    [
        "_LIST_",
        "_OF_",
        "_ENDPOINTS_"
    ]

    dcos {{ page.packageName }} --name={{ page.serviceName }} endpoints _ENDPOINT_
    {
        "address": ["10.0.3.156:_PORT_", "10.0.3.84:_PORT_"],
        "dns": ["_POD_-0.{{ page.serviceName }}.mesos:_PORT_", "_POD_-1.{{ page.serviceName }}.mesos:_PORT_"]
    }
    ```

1. _PROVIDE A SIMPLE EXAMPLE OF HOW TO CONNECT A CLIENT AND INTERACT WITH YOUR PRODUCT (E.G., WRITE DATA, READ DATA)._

## See Also

- [Connecting clients](https://docs.mesosphere.com/service-docs/{{ page.packageName }}/connecting-clients/)
