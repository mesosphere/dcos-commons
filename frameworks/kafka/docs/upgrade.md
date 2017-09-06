---
post_title: Upgrade `1.1.26-0.10.1.0-beta` to `1.1.27-0.11.0-beta` in DC/OS 1.10
enterprise: 'no'
---

This document explains how to upgrade the DC/OS Apache Kafka service from version `1.1.26-0.10.1.0-beta` to `1.1.27-0.11.0-beta` in DC/OS 1.10. Upgrading an existing Kafka service on a [strict security mode cluster](https://docs.mesosphere.com/1.9/security/#security-modes) requires an extra step. Refer to [Upgrade in Strict Mode](#upgrade-in-strict-mode) for more information.

##  Upgrade beta-kafka from 0.10.2.x to 0.11.0.0 in DC/OS 1.10


This section explains how to upgrade the DC/OS Apache Kafka service from Kafka 0.10.2.x to Kafka 0.11.0.0 in DC/OS 1.10.

- Refer to the Kafka [upgrade](https://kafka.apache.org/documentation/#upgrade) documentation for more information.

- Information about the new message format in 0.11.0.0 is available [here](https://kafka.apache.org/documentation/#upgrade_11_message_format).

- 0.11.0.0 clients can communicate with older brokers, such as 0.10.2.x brokers. But Kafka 0.11.0.0 introduces idempotent and transactional capabilities. 0.11.0.0 clients are required for these new features.

### Overview

This is an overview of the rolling upgrade process. The process for upgrading Kafka with DC/OS is documented in detail below.

1. Upgrade your package to the latest version of DC/OS Kafka service:
   * Upgrade Kafka software to `0.11.0.0`.
   * Do not update protocol and log versions yet.
   * Restart the brokers one at a time.    
   * Wait until the entire cluster is upgraded.

2. Change `inter.broker.protocol.version` to `0.11.0.0`:
   * In the `server.properties` file, change `inter.broker.protocol.version` to `0.11.0.0`.
   * Do not change log.message.format.version yet.
   * Restart the brokers one by one for the new protocol version to take affect.

3. Change `log.message.format.version` to `0.11.0`:
   * In the `server.properties` files, change `log.message.format.version` to `0.11.0`.
   * Restart the brokers one by one for the new log format version to take affect.

### Detailed upgrade instructions

The new DC/OS Kafka package `1.1.27-0.11.0-beta` (with Kafka version 0.11.0.0) has the following default settings:

* Kafka Version = 0.11.0.0
* inter.broker.protocol.version = 0.11.0.0
* log.message.format.version = 0.11.0

The beta DC/OS Kafka package `1.1.26-0.10.1.0-beta` (with Kafka version 0.10.2.x) has the following configuration:

* Kafka Version = 0.10.2.1
* inter.broker.protocol.version = 0.10.0.0
* log.message.format.version = 0.10.0


Here, we assume you already have beta DC/OS Kafka version 1.1.26-0.10.1.0-beta (with Kafka 0.10.2.x) installed. In order to guarantee no downtime during the upgrade process, follow the steps below.  

#### Step 1

First, install new cli for DC/OS Kafka version `1.1.27-0.11.0-beta`.

    dcos package install beta-kafka --package-version=1.1.27-0.11.0-beta --cli

 Make a note of the existing protocol and log versions. Upgrade your service to the new package version `1.1.27-0.11.0-beta` (with Kafka 0.11.0.0), but keep the existig protocol and log versions the same by applying customized package update options. The following `options.json` file shows how protocol and log versions can be customized. Here, we assume that existing service uses 0.10.0 protocol/log versions. If you have changed protocol and log to another 0.10.x version, set them in options JSON file.

    $ cat option.json
    {
        "kafka": {
            "inter_broker_protocol_version": "0.10.0.0",
            "log_message_format_version": "0.10.0"
        }
    }

Now, upgrade to package `1.1.27-0.11.0-beta`, which provides the new code, Kafka 0.11.0.0.

    $ dcos beta-kafka update start --options=options.json --package-version=1.1.27-0.11.0-beta --name=kafka

Since we updated with a customized options file, the protocol and log version are overwritten and not updated to the new package's default settings. The existing protocol and log version are specifically set in `options.json`.

The Kafka service will be upgraded to the version 0.11.0.0. The brokers will now be upgraded and restarted serially. `serial` is the default deployment plan strategy.

**Note**: The goal in using customized options during package upgrade is to keep the protocol and log versions the same. In the following steps, we will change protocol and log versions one at a time.

#### Step 2

Next, update the protocol version manually. Only change the value of `inter.broker.protocol.version` in the `options.json` file, and then perform an update operation. The brokers will be restarted one at a time with the new `server.properties` file with protocol version set to `0.11.0.0`.

    $ cat option.json
    {
        "kafka": {
            "inter_broker_protocol_version": "0.11.0.0",
            "log_message_format_version": "0.10.0"
        }
    }

    $ dcos beta-kafka update start --options=options.json  --name=kafka

#### Step 3    

Once you verify that all brokers are restarted, update the log version. Change the log version to `0.11.0` and perform another update operation.

    $ cat option.json
    {
        "kafka": {
            "inter_broker_protocol_version": "0.11.0.0",
            "log_message_format_version": "0.11.0"
        }
    }

    $ dcos beta-kafka update start --options=options.json --name=kafka

The new service will be running Kafka 0.11.0.0 with two customized options (`inter.broker.protocol.version` and `log.message.format.version`).

Since protocol and log versions have been updated with new protocol and log formats, you can not directly roll back to the previous package version.

**Note**: Default settings for protocol and log versions are overwritten with these customized options (steps 1 through 3), even though  their values are same as the defaults of the new package version `1.1.27-0.11.0-beta` . Pay attention to these customized options for further package upgrades, since they will be preserved unless overwritten explicitly.


##  Upgrade in Strict Mode from `1.1.26-0.10.1.0-beta` to `1.1.27-0.11.0-beta` in DC/OS 1.10

If you are upgrading to the DC/OS Apache Kafka package `1.1.27-0.11.0-beta`  on a DC/OS cluster running in [strict security mode](https://docs.mesosphere.com/1.9/security/#security-modes), you must specify service account details during the package update process. Service account credentials enable your service to authenticate to a DC/OS cluster in strict mode.


You can provide service account details in a JSON options file or via the DC/OS GUI.

### New Installation

If you are performing a fresh installation of Kafka `1.1.27-0.11.0-beta` on a strict mode cluster, add the following parameter to your options JSON file in order to  provide your service account details.


    $ cat options.json
    {
        "service": {
            "service_account": "this_is_your_service_account_id",
            "service_account_secret": "this_is_your_sa_secret_path"
        }
    }

    $ docs package install  beta-kafka --options=options.json ` --package-version=1.1.27-0.11.0-beta --name=kafka

**Note:** The syntax for specifying service account details has changed from the previous version. The `principal` and `secret_name` parameters have changed to `service_account` and `service_account_secret`. If you are upgrading your service from package version `1.1.26-0.10.1.0-beta`, you will need to specify service account details in a JSON options file.


### Upgrade to beta-kafka `1.1.27-0.11.0-beta` from `1.1.26-0.10.1.0-beta` in DC/OS 1.10

If you are upgrading your existing service running in strict mode, from `1.1.26-0.10.1.0-beta` to version `1.1.27-0.11.0-beta`, set `service_account` and `service_account_secret` in your options.  Add service_account and service_account_secret options only in Step 1 in [Upgrade Instructions](#upgrade-kafka-from-0.10.2.x-to-0.11.0.0
). Step 2 and Step 3 will be same. Modify Step 1 as follows if you are upgrading in strict mode.


    $ cat option.json
    {
        "service": {
            "service_account": "this_is_your_service_account_id",
            "service_account_secret": "this_is_your_sa_secret_path"
        },
        "kafka": {
            "inter_broker_protocol_version": "0.11.0.0",
            "log_message_format_version": "0.10.0"
        }
    }

   $ docs beta-kafka update start  --options=options.json --package-version=1.1.27-0.11.0-beta --name=kafka
