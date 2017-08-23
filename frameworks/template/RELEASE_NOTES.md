### Package Upgrade in Strict Mode

If you are upgrading to the latest version of Cassandra on a DC/OS cluster running in [strict security mode](https://docs.mesosphere.com/1.9/security/#security-modes), you must specify service account details during the package update process. Service account credentials enable your service to authenticate to a DC/OS cluster in strict mode.


You can provide service account details in a JSON options file or via the DC/OS GUI.


If you are **installing from scratch** in strict mode, add the following parameter to a JSON options file in order to  provide your service account details.

```
 $ docs package install  template --options=options.json ` --package-version=<this_package_version>

 $ cat options.json
  {
        "service": {
            "service_account": "this_is_your_service_account_id",
            "service_account_secret": "this_is_your_sa_secret_path"
        }
    }
```


**Note:** The syntax for specifying service account details has changed from the previous version. The `principal` and `secret_name` parameters have changed to `service_account` and `service_account_secret`. If you are upgrading your service from the previous version, you will need to specify service account details in a JSON options file.



If you are **upgrading your existing service** in strict mode, from a previous version to the this new version, set `service_account` and `service_account_secret` options.

  
```
  $ docs template update start  --options=options.json --package-version=<this_package_version>

  $ cat options.json
  {
        "service": {
            "service_account": "this_is_your_service_account_id",
            "service_account_secret": "this_is_your_sa_secret_path"
        }
    }
```


