##### Active Directory

Microsoft Active Directory can be used as a Kerberos KDC. Doing so requires creating a mapping between Active Directory users and Kerberos principals.

The utility [ktpass](https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/ktpass) can be used to both create a keytab from Active Directory and generate the mapping at the same time.

The mapping *can*, however, be created manually. For a Kerberos principal like `<primary>/<host>@<REALM>`, the Active Directory user should have its `servicePrincipalName` and `userPrincipalName` attributes set to,
```
servicePrincipalName = <primary>/<host>
userPrincipalName = <primary>/<host>@<REALM>
```

For example, with the Kerberos principal `{{ include.data.kerberos.principal }}`, then the correct mapping would be,
```
servicePrincipalName = {{ include.data.kerberos.spn }}
userPrincipalName = {{ include.data.kerberos.upn }}
```

If either mapping is incorrect or not present, the service will fail to authenticate that Principal. The symptom in the Kerberos debug logs will be an error of the form
```
KRBError:
sTime is Wed Feb 07 03:22:47 UTC 2018 1517973767000
suSec is 697984
error code is 6
error Message is Client not found in Kerberos database
sname is krbtgt/AD.MESOSPHERE.COM@AD.MESOSPHERE.COM
msgType is 30
```
when the `userPrincipalName` is set incorrectly, and an error of the form
```
KRBError:
sTime is Wed Feb 07 03:44:57 UTC 2018 1517975097000
suSec is 128465
error code is 7
error Message is Server not found in Kerberos database
sname is kafka/kafka-1-broker.confluent-kafka.autoip.dcos.thisdcos.directory@AD.MESOSPHERE.COM
msgType is 30
```
when the `servicePrincipalName` is set incorrectly.
