---
post_title: Uninstall
menu_order: 20
feature_maturity: preview
enterprise: 'no'
---

To uninstall a cluster, replace `name` with the name of the Elastic instance to be uninstalled.

```bash
dcos package uninstall --app-id=<name> elastic
```

Then, use the [framework cleaner script](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) to remove your Elastic instance from Zookeeper and destroy all data associated with it. The script requires several arguments. The default values to be used are:

- `framework_role` is `<service-name>-role`.
- `framework_principal` is `<service-name>-principal`.
- `zk_path` is `dcos-service-<service-name>`.

These values may vary if you customized them during installation. For instance, if you changed the Elastic service name to `customers`, then instead of

- `framework_role` is `elastic-role`.
- `framework_principal` is `elastic-principal`.
- `zk_path` is `dcos-service-elastic`.

you would use

- `framework_role` is `customers-role`.
- `framework_principal` is `customers-principal`.
- `zk_path` is `dcos-service-customers`.

If you are using the Enterprise Edition of DC/OS with authentication enabled, you will need to include the token in the GET command.

```bash
AUTH_TOKEN=$(dcos config show core.dcos_acs_token)
dcos node ssh --master-proxy --leader
docker run mesosphere/janitor /janitor.py -r elastic-role -p elastic-principal -z dcos-service-elastic --auth_token=$AUTH_TOKEN
```
