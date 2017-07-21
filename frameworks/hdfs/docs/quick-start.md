---
post_title: Quickstart
menu_order: 0
feature_maturity: experimental
enterprise: 'no'
---

This tutorial will get you up and running in minutes with HDFS. You will install and configure the DC/OS HDFS package and retrieve the core-site.xml and hdfs-site.xml files. These XML files are used to configure client nodes of the HDFS cluster.

**Prerequisites:**

-  [DC/OS and DC/OS CLI installed](https://docs.mesosphere.com/1.9/installing/) with a minimum of five private agent nodes, each with at least two CPU shares and eight GB of RAM available to the HDFS service.
-  Depending on your [security mode](https://docs.mesosphere.com/1.9/overview/security/security-modes/), HDFS requires a service authentication for access to DC/OS. For more information, see [Configuring DC/OS Access for HDFS](https://docs.mesosphere.com/service-docs/hdfs/hdfs-auth/).

   | Security mode | Service Account |
   |---------------|-----------------------|
   | Disabled      | Not available   |
   | Permissive    | Optional   |
   | Strict        | Required |

1.  Install the HDFS package.

    ```bash
    dcos package install hdfs
    ```

    **Tip:** Type `dcos hdfs` to view the HDFS CLI options.


1.  Show the currently configured HDFS nodes.

    ```bash
    dcos hdfs config list
    ```

    The output should resemble:

    ```bash
    [
      "1773cced-0805-4b36-9022-ce5f08cf373a"
    ]
    ```

1.  Configure HDFS on your nodes.

1.  [SSH](https://docs.mesosphere.com/1.9/administering-clusters/sshcluster/) to the leading master node.

    ```bash
    dcos node ssh --leader --master-proxy
    ```

1.  Pull the HDFS Docker container down to your node and start an interactive pseudo-TTY session.

    ```bash
    docker run -it mesosphere/hdfs-client:2.6.4 /bin/bash
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

    By default, the client is configured to be configured to connect to an HDFS service named `hdfs` and no further client configuration is required. If you want to configure with a different name, run this command with name (`<hdfs-name>`) specified:

    ```bash
    HDFS_SERVICE_NAME=<hdfs-name> ./configure-hdfs.sh
    ```

1.  Navigate to the Hadoop installation directory and list the contents.

    ```bash
    cd hadoop-2.6.4
    ./bin/hdfs dfs -ls /
    ```

    The output should resemble:

    ```bash
    Found 22 items
    -rwxr-xr-x   1 root  root           0 2017-06-15 18:23 /.dockerenv
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /bin
    drwxr-xr-x   - root  root        4096 2014-04-10 22:12 /boot
    -rw-rw-r--   1 root  root         364 2016-08-25 01:01 /configure-hdfs.sh
    drwxr-xr-x   - root  root         380 2017-06-15 18:23 /dev
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /etc
    drwxr-xr-x   - 10021 10021       4096 2017-06-15 18:23 /hadoop-2.6.4
    drwxr-xr-x   - root  root        4096 2014-04-10 22:12 /home
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /lib
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /lib64
    drwxr-xr-x   - root  root        4096 2015-12-08 09:38 /media
    drwxr-xr-x   - root  root        4096 2014-04-10 22:12 /mnt
    drwxr-xr-x   - root  root        4096 2015-12-08 09:38 /opt
    dr-xr-xr-x   - root  root           0 2017-06-15 18:23 /proc
    drwx------   - root  root        4096 2017-06-15 18:23 /root
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /run
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /sbin
    drwxr-xr-x   - root  root        4096 2015-12-08 09:38 /srv
    dr-xr-xr-x   - root  root           0 2017-06-15 16:11 /sys
    drwxrwxrwt   - root  root        4096 2017-06-15 18:23 /tmp
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /usr
    drwxr-xr-x   - root  root        4096 2017-06-15 18:23 /var
    ```

1.  To configure other clients, return to the DC/OS CLI and retrieve the `hdfs-site.xml` and `core-site.xml` files. Use these XML files to configure client nodes of the HDFS cluster.

    1.  Run this command to retrieve the `hdfs-site.xml` file.

        ```bash
        dcos hdfs endpoints hdfs-site.xml
        ```

        The output should resemble:

        ```
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
        dcos hdfs endpoints core-site.xml
        ```

        The output should resemble:

        ```
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
        <configuration>
            <property>
                <name>dfs.nameservice.id</name>
                <value>hdfs</value>
            </property>
            ...

        </configuration>
        ```
