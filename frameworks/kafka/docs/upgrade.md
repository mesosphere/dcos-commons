---
post_title: Upgrade Kafka from 0.10.2.1 to 0.11.0.0
menu_order: 10
feature_maturity: preview
enterprise: 'no'
---

# Upgrade Kafka from 0.10.2.1 to 0.11.0.0

This document explains upgrading the DC/OS Kafka service from 0.10.2.x to 0.11.0.0 version. Please follow the Kafka [upgrade](https://kafka.apache.org/documentation/#upgrade) documentation for more information. 

The rolling upgrade procedure (from 0.10.2.x to 0.11.0.0) is explained below. This is the recommended way of upgrading to 0.11.0.0 in order to guarantee no downtime while upgrading Kafka.


1. Upgrade the code to the new version:
   * Upgrade Kafka software to 0.11.0.0.
   * Restart the brokers one at a time.    
   * Wait until the entire cluster is upgraded.

2. Change inter.broker.protocol.version to 0.11.0.0:

   * Edit server.properties files and change inter.broker.protocol.version to 0.11.0.0.
   * Do not change log.message.format.version
   * Restart the brokers one by one for the new protocol version to take affect

3. Change log.message.format.version to 0.11.0:

   * Edit server.properties files and change log.message.format.version to 0.11.0.
   * Restart the brokers one by one for the new log format version to take affect



The new DC/OS Kafka package that we will be upgrading to has the following default settings:

* Kafka Version = 0.11.0.0
* inter.broker.protocol.version = 0.11.0.0
* log.message.format.version = 0.11.0

The current DC/OS Kafka service will typically have the following configuration:

* Kafka Version = 0.10.2.1
* inter.broker.protocol.version = 0.10.0.0
* log.message.format.version = 0.10.0


First, update your service to the new package version, but while doing that preserve the old protocol and log versions. In order to only update to the new code, apply customized package update options. The following `options.json` file shows how protocol and log versions can be customized. Here we assume that existing service use 0.10.0 protocol/log versions. If you have changed protocol and log to another 0.10.x version, please set them in options JSON file.
   
    {
        "kafka": {
            "inter.broker.protocol.version": "0.10.0.0",
            "log.message.format.version": "0.10.0"
        }
    }

Now, update to the package that provides the new code, Kafka 0.11.0.0, as follows. 

    $ dcos kafka update start --options=options.json --package-version=<new-kafka-package>

Since we update with a customized options file, protocol and log version are not changed to the pacakge's default settings 

The default strategy for a deployment plan is `serial`; therefore, the brokers will be updated serially, one at a time.


Next, update the protocol version. Only change the inter.broker.protocol.version in `options.json` file, and perform an update operation. The brokers will be restarted one at a time, with the new server.properties file that has protocol version set to 0.11.0.0. 
    
    {
        "kafka": {
            "inter.broker.protocol.version": "0.11.0.0",
            "log.message.format.version": "0.10.0"
        }
    }
    
   
    $ dcos kafka update start --options=options.json 
    
Once you verify that all brokers are restarted, update the log version. Change the log version to 0.11.0 and do another update operation. 
   
    {
        "kafka": {
            "inter.broker.protocol.version": "0.11.0.0",
            "log.message.format.version": "0.11.0"
        }
    }
    
   
    $ dcos kafka update start --options=options.json
    
    
    
    
    
    
