### Prerequisites
- [A DC/OS Service Account with a secret stored in the DC/OS Secret Store](https://docs.mesosphere.com/latest/security/ent/service-auth/custom-service-auth/).
- DC/OS Superuser permissions for modifying the permissions of the Service Account.

### Configure Transport Encryption

#### Setup the Service Account

[Grant](https://docs.mesosphere.com/1.10/security/ent/perms-management/) the service account the correct permissions.
- In DC/OS 1.10, the required permission is `dcos:superuser full`.
- In DC/OS 1.11+ the required permissions are:
```
dcos:secrets:default:/<service name>/* full
dcos:secrets:list:default:/<service name> read
dcos:adminrouter:ops:ca:rw full
dcos:adminrouter:ops:ca:ro full
```
where `<service name>` is the name of the service to be installed.

#### Install the service
Install the DC/OS {{ include.techName }} service including the following options in addition to your own:
```json
{
    "service": {
        "service_account": "<your service account name>",
        "service_account_secret": "<full path of service secret>",
        "security": {
            "transport_encryption": {
                "enabled": true,
                "allow_plaintext": <true|false default false>
            }
        }
    }
}
```
