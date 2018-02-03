---
layout: layout.pug
navigationTitle:
excerpt:
title: Quick Start
menuWeight: 40

package_name_pretty: YOUR_SERVICE
package_name: template
service_name: template
cli_package_name: template
---

## Prerequisites

- [DC/OS installed on your cluster](https://docs.mesosphere.com/latest/administration/installing/).

## Steps

1. If you are using open source DC/OS, install {{ page.package_name_pretty }} cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for information.

    ```bash
    dcos package install {{ page.package_name }}
    ```

    Alternatively, you can install {{ page.package_name_pretty }} from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

1. The service will now deploy with a default configuration. You can monitor its deployment via the Services tab of the DC/OS web interface.

1. Connect a client to {{ page.package_name_pretty }}.
    ```bash
    dcos {{ page.package_name }} endpoints
    [
        "_LIST_",
        "_OF_",
        "_ENDPOINTS_
    ]

    dcos {{ page.package_name }} endpoints _ENDPOINT_
    {
        "address": ["10.0.3.156:_PORT_", "10.0.3.84:_PORT_"],
        "dns": ["_POD_-0.{{ page.service_name }}.mesos:_PORT_", "_POD_-1.{{ page.service_name }}.mesos:_PORT_"]
    }
    ```

1. _PROVIDE A SIMPLE EXAMPLE OF HOW TO CONNECT A CLIENT AND INTERACT WITH YOUR PRODUCT (E.G., WRITE DATA, READ DATA)._

## See Also

- [Connecting clients](https://docs.mesosphere.com/service-docs/<Template>/connecting-clients/)
