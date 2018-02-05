---
layout: layout.pug
navigationTitle:
excerpt:
title: Quick Start
menuWeight: 40

package_name: beta-hdfs
service_name: hdfs
cli_package_name: beta-hdfs --name=hdfs
---

This tutorial will get you up and running in minutes with HDFS. You will install and configure the DC/OS HDFS package and retrieve the core-site.xml and hdfs-site.xml files. These XML files are used to configure client nodes of the HDFS cluster.

## Prerequisites

-  [DC/OS and DC/OS CLI installed](https://docs.mesosphere.com/latest/installing/) with a minimum of five private agent nodes, each with at least two CPU shares and eight GB of RAM available to the HDFS service.
-  Depending on your [security mode](https://docs.mesosphere.com/latest/overview/security/security-modes/), HDFS requires a service authentication token for access to DC/OS. For more information, see [Configuring DC/OS Access for HDFS](https://docs.mesosphere.com/services/hdfs/hdfs-auth/).

   | Security mode | Service Account |
   |---------------|-----------------------|
   | Disabled      | Not available   |
   | Permissive    | Optional   |
   | Strict        | Required |

## Steps

1.  Install the HDFS package.

    ```bash
    $ dcos package install {{ page.package_name }}
    ```

    **Tip:** Type `dcos {{ page.package_name }}` to view the HDFS CLI options.


1.  Show the currently configured HDFS nodes.

    ```bash
    $ dcos {{ page.cli_package_name }} config list
    ```

    The output should resemble:

    ```bash
    [
      "1773cced-0805-4b36-9022-ce5f08cf373a"
    ]
    ```

1.  Configure HDFS on your nodes.

1.  [SSH](https://docs.mesosphere.com/latest/administering-clusters/sshcluster/) to the leading master node.

    ```bash
    $ dcos node ssh --leader --master-proxy
    ```

1.  Pull the HDFS Docker container down to your node and start an interactive pseudo-TTY session.

    ```bash
    $ docker run -it mesosphere/hdfs-client:2.6.4 /bin/bash
    ```

    The output should resemble:

    ```bash
    Unable to find image 'mesosphere/hdfs-client:2.6.4' locally
    2.6.4: Pulling from mesosphere/hdfs-client
    6edcc89ed412: Pull complete
    bdf37643ee24: Pull complete
    ea0211d47051: Pull complete
    a3ed95caeb02: Pull complete
    12bd7c00b7e6: Pull complete
    9a93505f2bac: Pull complete
    9cc2baa935ae: Pull complete
    88e8b845a891: Pull complete
    9a84bc18aaba: Pull complete
    Digest: sha256:02384bc96d770e3e1fc6102b2019cdceea74e81f8223b8cdc330a499f1df733e
    Status: Downloaded newer image for mesosphere/hdfs-client:2.6.4
    ```

    By default, the client is configured to be configured to connect to an HDFS service named `{{ page.service_name }}` and no further client configuration is required. If you want to configure with a different name, run this command with name (`<hdfs-name>`) specified:

    ```bash
    $ HDFS_SERVICE_NAME=<hdfs-name> ./configure-hdfs.sh
    ```

1.  List the contents.

    ```bash
    $ ./bin/hdfs dfs -ls /
    ```

    The output should be empty.

1.  Create a file on HDFS.

    ```bash
    $ echo "Test" | ./bin/hdfs dfs -put - /test.txt
    ```

1.  List the contents again.

    ```bash
    $ ./bin/hdfs dfs -ls /
    ```

    The output should now resemble:

    ```bash
    Found 1 items
    -rw-r--r--   3 root supergroup          5 2017-08-25 17:41 /test.txt
    ```

1.  Read the file to ensure data integrity.

    ```bash
    $ ./bin/hdfs dfs -cat /test.txt
    ```

    The output should resemble:
    ```bash
    Test
    ```


1.  To configure other clients, return to the DC/OS CLI and retrieve the `hdfs-site.xml` and `core-site.xml` files. Use these XML files to configure client nodes of the HDFS cluster.

    1.  Run this command to retrieve the `hdfs-site.xml` file.

        ```bash
        $ dcos {{ page.cli_package_name }} endpoints hdfs-site.xml
        ```

        The output should resemble:

        ```xml
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?><configuration>
        <property>
        <name>dfs.nameservice.id</name>
        <value>hdfs</value>
        </property>
        ...

        </configuration>
        ```

    1.  Run this command to retrieve the `core-site.xml` file.

        ```bash
        $ dcos {{ page.cli_package_name }} endpoints core-site.xml
        ```

        The output should resemble:

        ```xml
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
        <configuration>
            <property>
                <name>fs.defaultFS</name>
                <value>hdfs://hdfs</value>
            </property>
            ...

        </configuration>
        ```
