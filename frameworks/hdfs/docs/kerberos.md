---
post_title: Kerberos
menu_order: 22
enterprise: 'yes'
---

# Setting up Apache Hadoop Distributed File System (HDFS) with Kerberos

The utility tool `kinit` needs to be installed on the agents and be accessible from within the tasks' containers in order for the system to deploy correctly.

## Create principals

In order to run Apache HDFS with Kerberos security enabled, a principal needs to be added for every service component in the cluster. The following principals are required (although the realm can be modified):
```
hdfs/name-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/name-0-zkfc.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-0-zkfc.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/name-1-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-1-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/name-1-zkfc.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-1-zkfc.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/journal-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/journal-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/journal-1-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/journal-1-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/journal-2-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/journal-2-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/data-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/data-0-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/data-1-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/data-1-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
hdfs/data-2-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/data-2-node.hdfs.autoip.dcos.thisdcos.directory@LOCAL
HTTP/api.hdfs.marathon.l4lb.thisdcos.directory@LOCAL
```
This cluster has 3 data nodes, though the correct number of principals should be added up to N data nodes.
(assuming a default service name of `hdfs`)

Note that the service name is part of the instance in the principal. The template of a principal with `hdfs` primary is:
```
hdfs/<pod-type>-<pod-index>-<task-type>.<service-name>.autoip.dcos.thisdcos.directory@<REALM>
```
Given a service name of `hdfs-demo`, the principal for a name node becomes:
```
hdfs/name-0-node.hdfs-demo.autoip.dcos.thisdcos.directory@LOCAL
```

Also note that if the service name is foldered, such as `myfolder/hdfs`, then the FQDN in the instance of the principal
becomes `myfolderhdfs`. For example:
```
hdfs/name-0-node.myfolderhdfs.autoip.dcos.thisdcos.directory@LOCAL
```

## Create the keytab secret

Once the principals have been created, a keytab file must be generated and uploaded to the DC/OS secret store as a base-64-encoded value. Assuming the keytab for **all** the HDFS principals has been created as a file `keytab`, this can be added to the secret store as follows (note that the DC/OS Enterprise CLI needs to be installed to gain access to the `security` command):
```bash
$ base64 -w keytab > keytab.base64
$ dcos security secrets create  __dcos_base64__keytab --value-file keytab.base64
```

The name of the secret created (`__dcos_base64__keytab`) can be changed, as long as the `__dcos__base64__` prefix is maintained.

## Deploy kerberized HDFS

Create the following `kerberos-options.json` file:
```json
{
    "service": {
        "name": "hdfs",
        "security": {
            "kerberos": {
                "enabled": true,
                "kdc": {
                    "hostname": "kdc.marathon.autoip.dcos.thisdcos.directory",
                    "port": 2500
                },
                "realm": "LOCAL",
                "keytab_secret": "__dcos_base64__keytab"

            }
        }
    }
}
```
Note the specification of the secret name as created in the previous step.

The kerberized Apache HDFS service is then deployed by running:
```bash
$ dcos package install hdfs --options=kerberos-options.json
```
