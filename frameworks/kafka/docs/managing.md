---
post_title: Managing
menu_order: 50
feature_maturity: preview
enterprise: 'no'
---


# Add a Broker

Increase the `BROKER_COUNT` value via the DC/OS web interface as in any other configuration update.

# Upgrade Software

1.  In the DC/OS web interface, destroy the Kafka scheduler to be updated.

1.  Verify that you no longer see it in the DC/OS web interface.

1.  Optional: Create a JSON options file with any custom configuration, such as a non-default `DEPLOY_STRATEGY`. <!--I'm getting this JSON from the app definition in the UI. The all caps looks a little odd, though -->

        {
            "env": {
                "DEPLOY_STRATEGY": "parallel-canary"
            }
        }


1.  Install the latest version of Kafka:

        $ dcos package install kafka -—options=options.json

# Graceful Shutdown
## Extend the Kill Grace Period

Increase the `brokers.kill_grace_period` value via the DC/OS CLI, i.e.,  to `60`
seconds. This example assumes that the Kafka service instance is named `kafka`.

During the configuration update, each of the Kafka broker tasks are restarted. During
the shutdown portion of the task restart, the previous configuration value for
`brokers.kill_grace_period` is in effect. Following the shutdown, each broker
task is launched with the new effective configuration value. Take care to monitor
the amount of time Kafka brokers take to cleanly shutdown. Find the relevant log
entries in the [Configure](configure.md) section.

Create an options file `kafka-options.json` with the following content:

        {
            "brokers": {
                "kill_grace_period": 60
            }
        }

Issue the following command:

        dcos kafka --name=/kafka update --options=kafka-options.json

## Restart a Broker with Grace

When restarting a Kafka broker, when the Kill Grace Period has been configured
to a period that sufficiently allows Kafka to cleanly shutdown, the broker's
shutdown time will be longer, but the subsequent startup time will be much more
rapid as the Byzantine reconciliation activities otherwise required for startup
can be safely skipped. An appropriate kill grace period has been configured allows Kafka to shut down cleanly. A graceful (or clean) shutdown takes longer than an ungraceful shutdown, but the next startup will be much quicker. This is because the complex reconciliation activities that would have been required are not necessary after graceful shutdown.

## Replace a Broker with Grace

The grace period must also be respected when a broker is shut down before replacement. While it is not ideal that a broker must respect the grace period even if it is going to lose persistent state, this behavior will be improved in future versions of the SDK. Broker replacement generally requires complex and time-consuming reconciliation activities at startup if there was not a graceful shutdown, so the respect of the grace kill period still provides value in most situations. We recommend setting the kill grace period only sufficiently long enough to allow graceful shutdown. Monitor the Kafka broker clean shutdown times in the broker logs to keep this value tuned to the scale of data flowing through the Kafka service.
