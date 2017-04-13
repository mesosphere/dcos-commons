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
