#### Place Service Keytab in DC/OS Secret Store

The DC/OS {{ include.techName }} service uses a keytab containing all node principals (service keytab). After creating the principals above, generate the service keytab making sure to include all the node principals. This will be stored as a secret in the DC/OS Secret Store.

*Note*: DC/OS 1.10 does not support adding binary secrets directly to the secret store, only text files are supported. Instead, first base64 encode the file, and save it to the secret store as `/desired/path/__dcos_base64__secret_name`. The DC/OS security modules will handle decoding the file when it is used by the service. More details [here](https://docs.mesosphere.com/services/ops-guide/overview/#binary-secrets).

The service keytab should be stored at `service/path/name/service.keytab` (as noted above for DC/OS 1.10, it would be `__dcos_base64__service.keytab`), where `service/path/name` matches the path and name of the service. For example, if installing with the options
```json
{
    "service": {
        "name": "a/good/example"
    }
}
```
then the service keytab should be stored at `a/good/example/service.keytab`.

Documentation for adding a file to the secret store can be found [here](https://docs.mesosphere.com/latest/security/ent/secrets/create-secrets/#creating-secrets-from-a-file-via-the-dcos-enterprise-cli).

*Note*: Secrets access is controlled by [DC/OS Spaces](https://docs.mesosphere.com/latest/security/ent/#spaces-for-secrets), which function like namespaces. Any secret in the same DC/OS Space as the service will be accessible by the service. However, matching the two paths is the most secure option. Additionally the secret name `service.keytab` is a convention and not a requirement.
