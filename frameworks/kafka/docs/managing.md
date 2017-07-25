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
## Extend Kill Grace Period

Increase the `brokers.kill_grace_period` value via the DC/OS CLI, i.e. to `60`
seconds. This example assumes that the Kafka service instance is named `kafka`.

During the reconfiguration, each of the Kafka broker tasks are restarted. During
the shutdown portion of the task restart, the previous configuration value for
`brokers.kill_grace_period` is in effect. Following the shutdown, each broker
task is launched with the new effective configuration value. So, care should be
taken to monitor the period of time that Kafka brokers are taking to cleanly
shutdown. The relevant log entries are enumerated within Configure.

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
can be safely skipped. Besides the observable differences in shutdown and startup
times, broker task restart should otherwise be the same as when the broker is not
granted sufficient grace.

## Replace a Broker with Grace

Similar to restarting a broker with grace, when replacing a broker, the grace
period must be respected during the shutdown. Respecting the grace period for
a clean shutdown for a broker that is intentionally being moved to another
task thus losing persisted state (if ROOT, not MOUNT volumes are in use) is
not perfect, but will be refined in further development of the Graceful Shutdown
feature of the DC/OS SDK, including the Kafka service implementation. Also,
broker replacement is generally performed due to machine or task or broker state
requiring the move to another task, so requiring the Byzantine reconciliation
activities at broker startup. In such cases, the shutdown portion of the restart
is likely to be rapid however may unecessarily cleanly shutdown or be killed
when the grace period completes. For this reason, the recommended approach to
setting the Kill Grace Period is to keep the period sufficiently long to allow
clean shutdown, but reasonably short, and monitor the Kafka broker clean
shutdown times (viewable in broker logs) to keep this value tuned to match the
scale of data flowing through the Kafka service.
