---
post_title: Limitations
menu_order: 100
feature_maturity: preview
enterprise: 'no'
---

# Limitations
_MANAGE CUSTOMER EXPECTIONS BY DISCLOSING ANY FEATURES OF YOUR PRODUCT THAT ARE NOT SUPPORTED WITH DC/OS, FEATURES MISSING FROM THE DC/OS INTEGRATION, ETC._

<a name="removing-a-node"></a>
## Removing a Node

Removing a node is not supported at this time.

<a name="updating-storage-volumes"></a>
## Updating Storage Volumes
Neither volume type nor volume size requirements may be changed after initial deployment.

<a name="rack-aware-replication"></a>
## Rack-aware Replication

Rack placement and awareness are not supported at this time.

<a name="updating-virtual-network"></a>
## Updating Virtual Network Configuration

When a pod from your service uses a virtual network, it does not use the port resources on the agent machine, and thus does not have them reserved. For this reason, we do not allow a pod deployed on a virtual network to be updated (moved) to the host network, because we cannot guarantee that the machine with the reserved volumes will have ports available. To make the reasoning simpler, we also do not allow for pods to be moved from the host network to a virtual network. Once you pick a network paradigm for your service the service is bound to that networking paradigm.

_CREATE SIMILAR SECTIONS FOR OTHER CAVEATS SPECIFIC TO YOUR PRODUCT INTEGRATION._
