### Package Upgrade in Strict Mode

If you are upgrading a service running in `strict` mode to the new package version (this version), you need to specify service account details as options during the package update process.


You need to give service account details in options in order to authenticate and run in DC/OS `strict` mode. Or you set them in the GUI. Here is how a service is installed using a previous package version:

```
 $ docs package install  kafka --options=options.json ` --package-version=<old_package_version_number>

 $ cat options.json
  {
        "service": {
            "principal": "this_is_your_service_account_id",
            "secret_name": "this_is_your_sa_secret_path"
        }
    }
```

In this new version, you need to set service account details as follows. 

```
 $ docs package install  kafka --options=options.json ` --package-version=<new_package_version_number>

 $ cat options.json
  {
        "service": {
            "service_account": "this_is_your_service_account_id",
            "service_account_secret": "this_is_your_sa_secret_path"
        }
    }
```

**Note**: "principal and secret_name" options are changed to "service_account and service_account_secret".

If you are upgrading your service to the new package version, you need to set these options again.
  
```
  $ docs kafka update start  --options=options.json --package-version=<new_package_version_number>

  $ cat options.json
  {
        "service": {
            "service_account": "this_is_your_service_account_id",
            "service_account_secret": "this_is_your_sa_secret_path"
        }
    }
```


