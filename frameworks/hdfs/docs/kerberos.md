---
layout: layout.pug
navigationTitle:
excerpt:
title: Kerberos
menuWeight: 22
---
{% assign data = site.data.services.hdfs %}

## Setting up Apache Hadoop Distributed File System (HDFS) with Kerberos

The utility tool `kinit` needs to be installed on the agents and be accessible from within the tasks' containers in order for the system to deploy correctly.

## Create principals

In order to run Apache HDFS with Kerberos security enabled, a principal needs to be added for every service component in the cluster. The following principals are required (although the realm can be modified):
```
hdfs/name-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/name-0-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-0-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/name-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/name-1-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/name-1-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/journal-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/journal-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/journal-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/journal-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/journal-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/journal-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/data-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/data-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/data-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/data-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
hdfs/data-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/data-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
HTTP/api.{{ data.serviceName }}.marathon.l4lb.thisdcos.directory@LOCAL
```
This cluster has 3 data nodes, though the correct number of principals should be added up to N data nodes.
(assuming a default service name of `{{ data.serviceName }}`)

Note that the service name is part of the instance in the principal. The template of a principal with `hdfs` primary is:
```
hdfs/<pod-type>-<pod-index>-<task-type>.<service-name>.autoip.dcos.thisdcos.directory@<REALM>
```
Given a service name of `{{ data.serviceName }}-demo`, the principal for a name node becomes:
```
hdfs/name-0-node.{{ data.serviceName }}-demo.autoip.dcos.thisdcos.directory@LOCAL
```

Also note that if the service name is foldered, such as `myfolder/{{ data.serviceName }}`, then the FQDN in the instance of the principal
becomes `myfolder{{ data.serviceName }}`. For example:
```
hdfs/name-0-node.myfolder{{ data.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
```

## Create the keytab secret

Once the principals have been created, a keytab file must be generated and uploaded to the DC/OS secret store as a base-64-encoded value. Assuming the keytab for **all** the HDFS principals has been created as a file `keytab`, this can be added to the secret store as follows (note that the DC/OS Enterprise CLI needs to be installed to gain access to the `security` command):
```bash
$ base64 -w0 keytab > keytab.base64
$ dcos security secrets create  __dcos_base64__keytab --value-file keytab.base64
```

The name of the secret created (`__dcos_base64__keytab`) can be changed, as long as the `__dcos__base64__` prefix is maintained.

## Deploy kerberized HDFS

Create the following `kerberos-options.json` file:
```json
{
    "service": {
        "name": "{{ data.serviceName }}",
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
$ dcos package install {{ data.packageName }} --options=kerberos-options.json
```

## Active Directory

Kerberized Apache HDFS also supports Active Directory as a KDC. Here the generation of principals and the relevant keytab should be adapted for the tools made available by the Active Directory installation.

As an example, the `ktpass` utility can be used to generate the keytab for the Apache HDFS principals as follows:
```bash
ktpass.exe                    /princ hdfs/name-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser name-0-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-00.keytab
ktpass.exe /in hdfs-00.keytab /princ HTTP/name-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-name-0-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-01.keytab
ktpass.exe /in hdfs-01.keytab /princ hdfs/name-0-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser name-0-zkfc@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-02.keytab
ktpass.exe /in hdfs-02.keytab /princ HTTP/name-0-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-name-0-zkfc@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-03.keytab
ktpass.exe /in hdfs-03.keytab /princ hdfs/name-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser name-1-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-04.keytab
ktpass.exe /in hdfs-04.keytab /princ HTTP/name-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-name-1-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-05.keytab
ktpass.exe /in hdfs-05.keytab /princ hdfs/name-1-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser name-1-zkfc@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-06.keytab
ktpass.exe /in hdfs-06.keytab /princ HTTP/name-1-zkfc.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-name-1-zkfc@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-07.keytab
ktpass.exe /in hdfs-07.keytab /princ hdfs/journal-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser journal-0-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass      /out hdfs-08.keytab
ktpass.exe /in hdfs-08.keytab /princ HTTP/journal-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-journal-0-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass /out hdfs-09.keytab
ktpass.exe /in hdfs-09.keytab /princ hdfs/journal-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser journal-1-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass      /out hdfs-10.keytab
ktpass.exe /in hdfs-10.keytab /princ HTTP/journal-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-journal-1-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass /out hdfs-11.keytab
ktpass.exe /in hdfs-11.keytab /princ hdfs/journal-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser journal-2-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass      /out hdfs-12.keytab
ktpass.exe /in hdfs-12.keytab /princ HTTP/journal-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-journal-2-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass /out hdfs-13.keytab
ktpass.exe /in hdfs-13.keytab /princ hdfs/data-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser data-0-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-14.keytab
ktpass.exe /in hdfs-14.keytab /princ HTTP/data-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-data-0-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-15.keytab
ktpass.exe /in hdfs-15.keytab /princ hdfs/data-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser data-1-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-16.keytab
ktpass.exe /in hdfs-16.keytab /princ HTTP/data-1-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-data-1-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-17.keytab
ktpass.exe /in hdfs-17.keytab /princ hdfs/data-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser data-2-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass            /out hdfs-18.keytab
ktpass.exe /in hdfs-18.keytab /princ HTTP/data-2-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser http-data-2-node@example.com /ptype KRB5_NT_PRINCIPAL +rndPass       /out hdfs-19.keytab
ktpass.exe /in hdfs-19.keytab /princ HTTP/api.{{ data.serviceName }}.l4lb.thisdcos.directory@EXAMPLE.COM /mapuser http-api@example.com /ptype KRB5_NT_PRINCIPAL +rndPass                              /out hdfs.keytab
```
Here it is assumed that the domain `example.com` exists and that the domain users (`name-0-node`, `http-name-0-node` etc.) have been created (using the `net user` command, for example).

The generated file `hdfs.keytab` can now be base64-encoded and added to the DC/OS secret store as above:
```bash
$ base64 -w0 hdfs.keytab > keytab.base64
$ dcos security secrets create  __dcos_base64__ad_keytab --value-file keytab.base64
```

Kerberized Apache HDFS can then be deployed using the following configuration options:
```json
{
    "service": {
        "name": "{{ data.serviceName }}",
        "security": {
            "kerberos": {
                "enabled": true,
                "kdc": {
                    "hostname": "active-directory-dns.example.com",
                    "port": 88
                },
                "realm": "EXAMPLE.COM",
                "keytab_secret": "__dcos_base64__ad_keytab"
            }
        }
    }
}
```
This assumes that the Active Directory server is reachable from the DC/OS cluster at `active-directory-dns.example.com` and is accepting connections on port `88`. Note also the change in Kerberos realm and the DC/OS secret path used for the keytab.
