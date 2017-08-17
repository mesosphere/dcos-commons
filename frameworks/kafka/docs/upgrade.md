---
post_title: Upgrade Kafka from 0.10.2.1 to 0.11.0.0
menu_order: 10
feature_maturity: preview
enterprise: 'no'
---

# Upgrade Kafka from 0.10.2.1 to 0.11.0.0


This document explains how to upgrade the DC/OS Kafka service from 0.10.2.x to 0.11.0.0 version. Refer to the Kafka [upgrade](https://kafka.apache.org/documentation/#upgrade) documentation for more information.

- Information about the new message format in 0.11.0.0 is available [here](https://kafka.apache.org/documentation/#upgrade_11_message_format).

- Kafka 0.11.0.0 introduces idempotent and transactional capabilities. These new 0.11.0.0 features require the new messaging format, and so will not work on older formats. 

- 0.11.0.0 clients can communicate with older brokers, such as 0.10.2.x brokers. However 0.11.0.0 clients are required for these new features. 

The recommended Kafka rolling upgrade from `0.10.2.x` to `0.11.0.0` includes the following steps:
1. Upgrade service the latest version of DC/OS Kafka package:
   * Upgrade Kafka software to `0.11.0.0`.
   * Do not update protocol and log versions, keep them same.
   * Restart the brokers one at a time.    
   * Wait until the entire cluster is upgraded.

2. Change `inter.broker.protocol.version` to `0.11.0.0`:
   * In the `server.properties` file, change `inter.broker.protocol.version` to `0.11.0.0`.
   * Do not change log.message.format.version yet.
   * Restart the brokers one by one for the new protocol version to take affect.

3. Change `log.message.format.version` to `0.11.0`:
   * In the server.properties files, change `log.message.format.version` to `0.11.0`.
   * Restart the brokers one by one for the new log format version to take affect.

This is an overview of the rolling upgrade process to guarantee no downtime during the upgrade. The process for upgrading Kafka with DC/OS is documented in detail below.


The new DC/OS Kafka package (with Kafka 0.11.0.0 version) that we will be upgrading to has the following default settings:

* Kafka Version = 0.11.0.0
* inter.broker.protocol.version = 0.11.0.0
* log.message.format.version = 0.11.0

The previous DC/OS Kafka package (with Kafka 0.10.2.x version) will have the following configuration:

* Kafka Version = 0.10.2.1
* inter.broker.protocol.version = 0.10.0.0
* log.message.format.version = 0.10.0


Here we assume that you already have a DC/OS Kafka service running, installed with the previous package version (with Kafka 0.10.2.x).  In order to guarantee no downtime during the upgrade process (upgrade service to the new DC/OS Kafka package with 0.11.0.0), follow the steps described below.  

#### Step 1

First, make a note of the existing protocol and log versions. Upgrade your service to the new package (with Kafka 0.11.0.0) but keep the existig protocol and log versions same. In order to only update to the new code, apply customized package update options. The following `options.json` file shows how protocol and log versions can be customized. Here we assume that existing service uses 0.10.0 protocol/log versions. If you have changed protocol and log to another 0.10.x version, set them in options JSON file.
   
    {
        "kafka": {
            "inter.broker.protocol.version": "0.10.0.0",
            "log.message.format.version": "0.10.0"
        }
    }

Now, upgrade to the package that provides the new code, Kafka 0.11.0.0. 

    $ dcos kafka update start --options=options.json --package-version=<new-kafka-package-with-0.11.0.0>

Since we updated with a customized options file, protocol and log version are overwritten and not updated to the new package's default settings. The existing protocol and log version are specifically set in `options.json`. 

The Kafka service will be upgraded to the new package version. This will upgrade the code to the new 0.11.0.0 version. The brokers will now be upgraded and restarted serially, one at a time. `serial` is the default deployment plan strategy.

**Note**: The goal in using customized options during package upgrade is to keep protocol and log versions same. We will enforce the new format to take affect gradually in the following steps.


#### Step 2

Next, update the protocol version manually. Only change the value of `inter.broker.protocol.version` in the `options.json` file, and then perform an update operation. The brokers will be restarted one at a time, with the new `server.properties` file that has protocol version set to `0.11.0.0`. 
    
    {
        "kafka": {
            "inter.broker.protocol.version": "0.11.0.0",
            "log.message.format.version": "0.10.0"
        }
    }
    
   
    $ dcos kafka update start --options=options.json 
    
#### Step 3    
    
Once you verify that all brokers are restarted, update the log version. Change the log version to `0.11.0` and do another update operation. 
   
    {
        "kafka": {
            "inter.broker.protocol.version": "0.11.0.0",
            "log.message.format.version": "0.11.0"
        }
    }
    
   
    $ dcos kafka update start --options=options.json
    
    
The new service will be running the Kafka 0.11.0.0 with two specific customized options (`inter.broker.protocol.version` and `log.message.format.version`). 

Since protocol and log versions have been updated (new protocol and log formats), you can not directly roll back to the previous package version.

**Note**: Default settings for protocol and log versions are overwritten with these customized options (steps 1 through 3), even though  their values are same as the defaults of the new package. Pay attention to these customized options for further package upgrades since they will be preserved unless overwritten explicitly.

    
    
