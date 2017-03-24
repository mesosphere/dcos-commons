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

1.  If you are using the enterprise edition, create an JSON options file with your latest configuration and set your plan strategy to "STAGE"

        {
            "service": {
                "phase_strategy": "STAGE"
            }
        }


1.  Install the latest version of Kafka:

        $ dcos package install kafka -—options=options.json


1.  Roll out the new version of Kafka in the same way as a configuration update is rolled out. See Configuration Update Plans.
