---
post_title: Troubleshooting
menu_order: 70
feature_maturity: preview
enterprise: 'no'
---

The Kafka service will be listed as "Unhealthy" when it detects any underreplicated partitions. This error condition usually indicates a malfunctioning broker. Use the `dcos kafka topic under_replicated_partitions` and `dcos kafka topic describe <topic-name>` commands to find the problem broker and determine what actions are required.

Possible repair actions include `dcos kafka broker restart <broker-id>` and `dcos kafka broker replace <broker-id>`. The replace operation is destructive and will irrevocably lose all data associated with the broker. The restart operation is not destructive and indicates an attempt to restart a broker process.

# Configuration Update Errors

The bolded entries below indicate the necessary changes needed to create a valid configuration:

<pre>
$ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
GET /service/kafka/v1/plan HTTP/1.1

{
    "phases": [
        {
             "id": "c26bec40-3290-4501-b3da-945d0abef55f",
            "name": "Reconciliation",
            "steps": [
                {
                    "id": "e56d2e4a-e05b-42ad-b4a0-d74b68d206af",
                    "message": "Reconciliation complete",
                    "name": "Reconciliation",
                    "status": "COMPLETE"
                },
                "status": "COMPLETE"
            ]
        },
        {
            
            "id": "226a780e-132f-4fea-b584-7712b07cf357",
            "name": "Update to: 72cecf77-dbc5-4ae6-8f91-c88702b9a6a8",
            "steps": [
                {
                    "id": "d4e72ee8-4608-423a-9566-1632ff0ab211",
                    "message": "Broker-0 is COMPLETE",
                    "name": "broker-0",
                    "status": "COMPLETE"
                },
                {
                    "id": "3ea30deb-9660-42f1-ad23-bd418d718999",
                    "message": "Broker-1 is COMPLETE",
                    "name": "broker-1",
                    "status": "COMPLETE"
                },
                {
                    "id": "4da21440-de73-4772-9c85-877f2677e62a",
                    "message": "Broker-2 is COMPLETE",
                    "name": "broker-2",
                    "status": "COMPLETE"
                }
            ],
            "status": "COMPLETE"
        }
    ],
    
    <b>"errors": [
        "Validation error on field \"BROKER_COUNT\": Decreasing this value (from 3 to 2) is not supported."
    ],</b>
    <b>"status": "Error"</b>
}
</pre>

# Replacing a Permanently Failed Server

If a machine has permanently failed, manual intervention is required to replace the broker or brokers that resided on that machine. Because DC/OS Kafka uses persistent volumes, the service continuously attempts to replace brokers where their data has been persisted. In the case where a machine has permanently failed, use the Kafka CLI to replace the brokers.

In the example below, the broker with id `0` will be replaced on new machine as long as cluster resources are sufficient to satisfy the service’s placement constraints and resource requirements.

    $ dcos kafka broker replace 0

