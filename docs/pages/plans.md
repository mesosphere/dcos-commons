---
title: Plans and Deployment
menuWeight: 1
toc: true
---

Plans are how SDK Services convey progress through service management operations, such as repairing failed tasks and/or rolling out changes to the service's configuration. This document describes what Plans are, how they work, and how they can be used in the context of running a service as an operator or building a new service as a developer.

This document focuses on the mechanics of how Plans actually work behind the scenes. For more general information about customizing Plans in the context of developing a service, see the [developer guide](../developer-guide/#plans).

## Overview

Each Plan is a tree with a fixed three-level hierarchy of the Plan itself, its Phases, and then Steps within those Phases. These are all collectively referred to as "Elements". The choice of three levels was arbitrarily chosen as "enough levels for anybody". The fixed tree hierarchy was chosen in order to simplify building UIs that display plan content. In particular, lots of suggestions were made to have a full DAG structure, which were ultimately rejected. This three-level hierarchy can look as follows:

```
Plan foo
├─ Phase bar
│  ├─ Step qux
│  └─ Step quux
└─ Phase baz
   ├─ Step quuz
   ├─ Step corge
   └─ Step grault
```

Plans are advertised by the Scheduler via `/v1/plans` endpoints. In practice, most (but not all) work performed by the Scheduler is conveyed via Plans. A given service can have multiple Plans, all doing work in parallel. This work can include deploying the service, rolling out configuration changes or upgrades, relaunching failed or replaced tasks, decommissioning pods, and uninstalling the service. The developer can even define their own custom plans to be manually invoked by the operator. If the Scheduler is running [multiple services](../multi-service/), then each of those services will have its own independent set of Plans, accessible at `/v1/multi/<svcname>/plans`.

Plans as a concept have several benefits:
- They allow operators to see what the service is currently doing, and to visualize the broader operation such as for a config rollout. The fixed structure of that information meanwhile makes it straightforward to build UIs and tooling on top.
- They allow operators to assign the state of running operations via editing the state of Plan elements. For example, a stuck uninstall could be updated to stop waiting to clean up resources that no longer exist in the cluster.
- They allow developers to customize how common operations are performed, or to define new custom operations that can be triggered by end-users. For more information on this, see the [developer guide](../developer-guide/#plans).

## Statuses

All plan elements have a Status value. However, the Plan status is solely determined based on the statuses of its child Phases, and the Phases in turn determine their statuses based on their Steps. For example, a Phase with 3 `PENDING` Steps will also be `PENDING`, but once one of those Steps moves to e.g. `STARTING`, then the parent Phase will be `IN_PROGRESS`. This logic is handled by [PlanUtils.getAggregateStatus()](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/plan/PlanUtils.java#L93).

The Status values are what drive the determination of what work needs to be done at any given time. For example, a Step that's `COMPLETE` will be ignored by the scheduler, whereas a step that's `PENDING` or `PREPARED` will be considered part of the active work set.

The Scheduler offers a Plans API which can be used to manually override statuses. For example, the `plan force-complete` command can be used to force a Step into a `COMPLETE` step. The scheduler will then consider this step complete and will move on to other work. Similarly, the `plan restart` command can be used to set a Step back to `PENDING` so that the Scheduler is forced to invoke that Step a second time. For some examples of this manipulation, see [Operations](#operations).

A full list of statuses can be found in [Status.java](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/plan/Status.java).

## Strategies

When deciding what work to do next, the Scheduler polls the Plan for a list of Candidate Steps. Candidate Steps are the steps which are due to be processed. The Plan uses its assigned Strategy to pick the candidate Phases which then use their respective Strategies to decide their candidate Steps. The Scheduler then attempts to process the list of Candidate Steps that it got back.

These Strategies are effectively implementations of some dependency structure, based on the statuses of the elements they're wrapping. For example, a parallel Strategy would return all elements that haven't been completed yet, while a serial Strategy would only return the next incomplete element in some ordered sequence. For example, the following Plan shows a mix of serial and parallel strategies:

```
foo (serial strategy) (IN_PROGRESS)
├─ bar (serial strategy) (COMPLETE)
│  ├─ Step qux (COMPLETE)
│  └─ Step quux (COMPLETE)
└─ baz (parallel strategy) (IN_PROGRESS)
   ├─ Step quuz (STARTING)
   ├─ Step corge (PENDING)
   └─ Step grault (COMPLETE)
```

Plan `foo` has a serial strategy, so it wants to run Phase `bar` before Phase `baz`. In this case, we see that Phase `bar` is already `COMPLETE`d, so `baz` is selected as a Phase to be checked for candidate Steps. Phase `baz` has a parallel Strategy, so it returns all non-`COMPLETE` Steps. In this case, that's `quuz` and `corge`, so those would be returned to the Scheduler as the current Candidate Steps to be run.

### Default strategies

The following strategies are available by default, these can be used in a Service's YAML definition:
- `serial` and `parallel`
- `serial-canary` (or just `canary`) and `parallel-canary`

The `-canary` variants produce a basic canary rollout where a single element is allowed to launch before any other elements may launch. When a canary strategy is used, the elements will be waiting for the operator to run `plan continue` commands. The first `plan continue` would cause the Strategy to mark the first element as eligible, then a second `plan continue` would allow the rest of the rollout to complete normally.

### Custom strategies

In addition to those defaults, a Java developer can implement their own custom Strategy. For example custom strategies are used to define the dependencies in uninstall and decommission plans generated by the SDK.

## Plan execution

We've looked at how the Scheduler uses Strategies to select any Steps to be executed, but how does that execution work in practice?

The first _step_ is to figure out what Steps are candidates for doing work. This is done by looking at their statuses. After the Steps have been selected, they will be invoked directly via their `start()` function.
Steps can optionally have a `PodInstanceRequirement`, which contains information about a pod of tasks that the Step wants to deploy.

Some steps, but not all, represent the work needed to deploy tasks in the cluster. This is ultimately determined by the presence of a `PodInstanceRequirement`, which defines the required footprint for the pod, and the task(s) that should be launched into that pod. If the Step has this, then the Scheduler will evaluate offers against that `PodInstanceRequirement` and then notify the Step whether that evaluation succeeded by invoking its `updateOfferStatus()` method. This lets the Step know that a launch has started. As the Scheduler receives `TaskStatus` messages from Mesos about the task, those will automatically be passed to the `Step`. For example, if the Step is expecting the task to enter a `TASK_RUNNING` state, the Step can mark itself `COMPLETE` once it has received this `TaskStatus`.

In short, the flow works as follows:
1. Get Plans from PlanManagers
    - Plans may be dynamically generated, e.g. `recovery`
2. Get active Steps from Plans: determined by Strategies in Plans/Phases
    - `serial`, `parallel`, custom(`dependency`), ...
3. Filter any Steps which operate on the same Assets (Tasks)
    - Avoid multiple Steps working on the same Task at the same time
    - Priority: `deploy`, `recovery`, `decommission`, custom plans
4. Execute the Steps, fetch any `PodInstanceRequirement`s
    - Evaluate `PodInstanceRequirement` (if any) against Offers
    - Notify Step whether any Offer did or didn't match

### Plan parallelism

By default, services come with two plans up-front. One is the `deploy` plan, which deals with rolling out changes to the service's configuration, and the other is the `recovery` plan, which automatically recovers tasks that have exited or been restarted by the user. If pods are being decommissioned, a third plan named `decommission` will also be added.

These plans will all be processed in parallel, where Steps from each plan are fetched and then executed in a single pass. However, some of these Steps may be doing work on the same underlying object, e.g. one Step may be trying to update a given pod, while another may be trying to tear it down. To avoid contention, we have the concept of Dirtied Assets, where two Steps cannot be performing work on the same object (a Pod in this case) at the same time. This is enforced in the [`PlanCoordinator`](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/plan/DefaultPlanCoordinator.java), which selects candidate Steps from all plans defined in the Scheduler. The priority of plans is defined as follows: `deploy`, `recovery`, `decommission`, and then any custom plans. For example if we're in the middle of deploying a new version of a task in the `deploy` plan, there isn't much point in trying to recover the old version of that task in the `recovery` plan.

But why would we want these to run in parallel in the first place? This is intentional. The main reason is so that work in one plan isn't necessarily blocked waiting on work in another plan. For example, there isn't any reason that the Scheduler can't be updating pod-2-task while it also recovers pod-4-task. But per above we want to avoid having multiple plans performing operations to the same tasks at the same time.

## How deployment works

Deployment, as handled by the `deploy` plan, is defined as moving the service from its current state to some target configuration. In the case of initial install, the `deploy` plan is moving from a null configuration to an initial deployment. When reconfiguring the service, it's moving from one or more previous configurations to a new target configuration. Finally, even uninstall is considered a "deployment" where the service is being moved back to a null configuration.

This deployment depends on the ability of the scheduler to detect the current configuration of launched tasks, so that it knows which ones need to be deployed. The way this works is each task's `TaskInfo` is stored in ZK with a `target_configuration` label, which points to the UUID of the config which was used to generate and launch that task. The Scheduler can cross-reference the stored UUIDs with its history of prior configurations to figure out whether a given task needs to be updated -- this is what is ultimately used to populate the `deploy` plan with in`COMPLETE` Steps for any operations to be performed. Similarly, this is what the `recovery` plan uses in its role of relaunching tasks into their _current_ configuration.

Once the `deploy` Plan starts, it will begin the process of moving tasks to the new target config, according to the Strategies configured in the Plan itself. Meanwhile, the `recovery` plan will also be active, but it _only_ relaunch failed tasks into their current configuration. So if the `deploy` plan is currently upgrading `node-3` and `node-5` fails, then the `recovery` plan will relaunch `node-5` back into the old config, not the new one. This is intentional, as we want the `deploy` plan to have full control over the upgrade process. If the `recovery` plan was automatically relaunching tasks into the new configuration, then the steps laid out in the `deploy` Plan would effectively be getting overridden in the process.

## Defining Plans

The Service developer is expected to provide plans for any custom behavior they require. If these are not provided, default plans will be automatically generated based on the declared pods. Plan customizations may be defined within the YAML service specification, or using the SDK Java APIs.

### Default Plans

By default, the `deploy` plan will be populated with a reasonable default. The default will be to sequentially deploy each of the declared pods in the order that they were declared in the YAML specification. Any declared readiness checks and goal states will be honored as the rollout is performed.

For basic services, this behavior is typically "good enough". Service developers can change this default behavior using the YAML service specification or via the Java API as described below.

### Custom YAML Plans

The developer can specify custom plans in their YAML service specification. These can either be used to override default plans, or to define new plans that can be manually invoked by the operator.

- A YAML `deploy` Plan will override the behavior of initial deployment and configuration changes. A separate `update` plan may also be specified, in which case `deploy` will handle initial deployment and `update` will handle configuration changes.
- If the YAML Plan has a custom name, then it can be manually launched by the operator. The operator may specify environment variables in their request which will be passed to the tasks run by the plan. One caveat of this is that the scheduler doesn't currently know what environment variables are expected so there is no validation that values required by the operation have actually been provided.

For more information on the syntax for declaring plans in the YAML service specification, see the [YAML Reference](../yaml-reference). For theoretical examples of custom YAML plans, see the [Developer Guide](../developer-guide#custom-deployment-plan).
The [Cassandra service specification](https://github.com/mesosphere/dcos-commons/blob/4910aeb/frameworks/cassandra/src/main/dist/svc.yml#L266) provides an example of a customized `deploy` plan (additional `init_system_keyspaces` step for `node-0`), as well as several custom Plans which may be manually triggered by the operator.

### Custom Java Plans

While YAML plans allow the developer to statically declare new plans and/or override default plans like `deploy` and `update`, the Java Plan APIs allow the developer to customize things further.

The Java APIs allow the service developer to:
- Customize dynamic plans like `recovery` to define custom recovery behavior when certain tasks fail. By default, the `recovery` plan just relaunches the task to get it to the desired goal state (e.g. keep it `RUNNING`, or relaunch it until it successfully `FINISH`es), but some services may require that additional operations be performed in certain cases.
- Use a custom dependency structure (other than `serial` or `parallel`) in Plans/Phases.

For example, the developer could specify a `PlanCustomizer` which edits the content of a plan before it's used by the scheduler itself, or the developer can simply add a set of plans when first creating the service.

[`UninstallPlanFactory`](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/uninstall/UninstallPlanFactory.java) and [`DecommissionPlanFactory`](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/decommission/DecommissionPlanFactory.java) each provide similar examples of constructing Plans in Java which use custom Strategies.

## Use cases

Now that we have a basic understanding of how the Plans are structured, how are they put to practice? Lets look at some common scenarios and see how they behave.

### Initial deployment

First, we can look at initial deployment of a `hello-world` service with 1 `hello` pod and 2 `world` pods. "Initial deployment" means that this is the first time the service is being launched into the cluster. It doesn't have any existing resources to launch against, so it will just look for anything that matches.

When the service starts, it would be initialized with the following `deploy` plan:
```
deploy (serial strategy) (PENDING)
├─ hello (serial strategy) (PENDING)
│  └─ hello-0:[server] (PENDING)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

At this point, the service uses the Plan/Phase strategies to select the active Step(s). We can see that the `deploy` Plan has a `serial` strategy, so it would select `hello`, the first incomplete Phase. The `hello` phase meanwhile would select `hello-0`, the first incomplete Step.

In deploying `hello-0`, the service would fetch the `PodInstanceRequirement` returned by that Step, which would contain the footprint required by the pod and the instruction to launch `hello-0-server` task within that pod. The service will tell the `hello-0` Step that it's now processing offers on the Step's behalf, and the Step will update its internal state to `PREPARED` to reflect that it's currently being evaluated:
```
deploy (serial strategy) (IN_PROGRESS)
├─ hello (serial strategy) (IN_PROGRESS)
│  └─ hello-0:[server] (PREPARED)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

Once the service has found a Mesos offer which matches the Step's `PodInstanceRequirement`, the service would tell Mesos to reserve the required resources and to launch the `hello-0-server` task. The service would then notify the `hello-0` Step that the launch had occured. The Step will set its internal state to `STARTING` to reflect that it's waiting for the task to actually launch:
```
deploy (serial strategy) (STARTING)
├─ hello (serial strategy) (STARTING)
│  └─ hello-0:[server] (STARTING)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

At some point in the near future, Mesos will (hopefully) send us a `TaskStatus` for `hello-0` saying that it's `RUNNING`. If the `hello-0` pod has a readiness check defined, its Step will be set to `STARTED` and we will wait for another `TaskStatus` saying that the readiness check had completed. If there is no readiness check then the Step will immediately go to `COMPLETE`. For this example we will assume there is a readiness check defined:
```
deploy (serial strategy) (STARTED)
├─ hello (serial strategy) (STARTED)
│  └─ hello-0:[server] (STARTED)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

Eventually, the readiness check passes and the Step is marked `COMPLETE`. If the readiness check never passed, then the Scheduler would wait indefinitely. The Operator could intervene in a couple ways: 1) Restart the task so that it gets relaunched and can try the readiness check again or 2) force-complete the Step if the lack of readiness check should be ignored. However the latter comes with several risks and should only be used in cases where the readiness check should indeed be ignored. This is explained further in [Operations](#operations).
```
deploy (serial strategy) (IN_PROGRESS)
├─ hello (serial strategy) (COMPLETE)
│  └─ hello-0:[server] (COMPLETE)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

Now that the `hello` phase is `COMPLETE` the `deploy` Plan strategy will return the `world` Phase, and that Phase will return the `world-0` Step. Deployment will proceed with `world-0` in that Phase until it is `COMPLETE`, and then will move to `world-1`. As can be seen in the plan, these Steps would launch 2 tasks into each of the `world` pods; one named `server` and the other named `world-N-sidecar`. At this point the `deploy` plan will be `COMPLETE` and the service will be fully deployed:
```
deploy (serial strategy) (COMPLETE)
├─ hello (serial strategy) (COMPLETE)
│  └─ hello-0:[server] (COMPLETE)
└─ world (serial strategy) (COMPLETE)
   ├─ world-0:[server, sidecar] (COMPLETE)
   └─ world-1:[server, sidecar] (COMPLETE)
```

### Configuration change

Now that we have a deployed service, we could make the following changes:
- Increase the number of `hello` pods from 1 to 2.
- Double the CPU resources of the `world` pods from 1 to 2 CPUs.

This change will manifest as a restart of the Scheduler process. The Scheduler will be relaunched with updated envvars for each change to the configuration. The envvars will all get rendered into the YAML spec (parsed as a `RawServiceSpec`), which will then be internally converted into the lower-level `ServiceSpec`.

The Scheduler will then compare this new `ServiceSpec` configuration against the previous `ServiceSpec`(s) referenced by UUID in the current tasks. The two changes will be detected and the Scheduler will generate a `deploy` plan that looks like the following:
```
deploy (serial strategy) (IN_PROGRESS)
├─ hello (serial strategy) (IN_PROGRESS)
│  ├─ hello-0:[server] (COMPLETE)
│  └─ hello-1:[server] (PENDING)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

The Scheduler will then execute this plan. For the new `hello-1` pod, it's a new deployment the first valid location will be used. Meanwhile the two `world` pods will specifically be relaunched in their current locations. This is enforced behind the scenes by looking for existing `resource_id` labels in offered `ReservationInfo`s.

Finally, once the changes have been rolled out, the `deploy` Plan will return again to a `COMPLETE` state. But what happens if we changed our minds partway through this deployment? For example, we realized that 2 CPUs was too much for the world tasks, and that 1.5 CPUs would be better, but `world-1` hadn't been deployed with the new change yet.

In that situation, the Scheduler would be restarted again, and it would have a mix of tasks across two different configurations.
- `hello-0`, `hello-1` up to date (no changes there since last deployment)
- `world-0` has 2.0 CPUs, want 1.5
- `world-1` has 1.0 CPUs, want 1.5

As such, the Scheduler would create a new `deploy` plan as follows, moving both of the `world` pods to the _current_ configuration, regardless of what their prior configuration may be:
```
deploy (serial strategy) (IN_PROGRESS)
├─ hello (serial strategy) (COMPLETE)
│  ├─ hello-0:[server] (COMPLETE)
│  └─ hello-1:[server] (COMPLETE)
└─ world (serial strategy) (PENDING)
   ├─ world-0:[server, sidecar] (PENDING)
   └─ world-1:[server, sidecar] (PENDING)
```

The `world-0` pod will be updated to have 1.5 CPUs from its prior allocation of 2.0, and then the `world-1` pod will be updated to have 1.5 CPUs from its prior allocation of 1.0.

### Recovery: Pod Restart/Replace

As hinted at elsewhere, the `recovery` Plan is special because starts in an empty state and is later automatically populated with any detected tasks that need to be recovered. At the moment, completed operations can remain as `COMPLETE` phases in the `recovery` plan indefinitely until the scheduler process is restarted. To show an example of how the `recovery` plan typically opeprates we can also look at what happens when an operator requests a `pod restart` or `pod replace` operation. These operations rely on the `recovery` Plan to relaunch the task. As such, the recovery of restarted/replaced pods can likewise be customized by the developer.

For `pod restart` (kill and relaunch pod at current location), the sequence works as follows:
1. Mesos is told to kill task
2. Scheduler receives a terminal `TaskStatus`, typically `TASK_KILLED`.
3. `recovery` plan is automatically populated with a Phase to relaunch the task.
4. The `RecoveryStep`'s `PodInstanceRequirement` specifies `RecoveryType.TRANSIENT`. Offer evaluation sees this and looks for an offer containing `resource_id`s matching the original task.
5. Once an Offer for the task's original resources is found, the task is relaunched back into that footprint.

Meanwhile, `pod replace` (kill and destroy pod, launch with fresh slate) is similar, with some differences:
- Before Mesos is told to kill the task, the `TaskInfo` in ZK is updated to contain a `permanently-failed` label.
- The `PodInstanceRequirement` in the `RecoveryStep` has `RecoveryType.PERMANENT`. The offer evaluator therefore looks for any Offer which can be used to launch the task from scratch.
- The previous resources used by the pod are garbage collected (unreserved) if/when offered by Mesos.

### Uninstall

The Uninstall flow is displayed via the "`deploy`" Plan when the service is uninstalling. This can be confusing, but there are two reasons for doing this:
- The `deploy` Plan can be thought of as dealing with transitioning the service between configurations. In this case, the target configuration is `NULL`. This is a bit pedantic, but there's a better reason...
- In practice, the `deploy` Plan is what's checked to see how a change to the service is proceeding.

The Uninstall process can be summarized as follows:
1. Kill all tasks
2. Wait to be offered the reserved resources of those tasks. Unreserve those resources as they are offered.
3. Once there are no known resources left to be unreserved, tell Mesos to tear down the framework.
4. Advertise a completed `deploy` Plan. could arguably be called `uninstall`, but `deploy` is what's used everywhere else...)
5. The Scheduler's Marathon app is automatically deleted by Cosmos, the DC/OS packaging service, which polls the `deploy` plan during uninstall to detect completion.

The reason for this long and involved process is ultimately due to issues with Mesos itself:
1. First, resources are reserved against _roles_, not _frameworks_. Therefore, simply destroying the Mesos framework doesn't automatically clean up the resources which that framework had reserved, despite the fact that SDK frameworks always have a 1:1 relationship with roles. This is why the manual dereservations are required in the first place.
2. Second, Mesos never simply _tells_ the framework what's currently reserved, instead the reservations are _offered_ via the non-deterministic offer cycle. The best the Scheduler can do is keep a snapshot of what resources it _thinks_ it has, and to treat that as a checklist when the service is being uninstalled. This can then lead to problems when the Scheduler's view of its resources isn't aligned with Mesos' view. For example, an uninstalling Scheduler can end up indefinitely stuck waiting to be offered resources on an agent system which no longer exists. The operator can get out of this situation by issuing `plan force-complete` calls for any Steps representing the nonexistent resources, and thereby override what the Scheduler thinks is the state of its reservations, since it cannot simply fetch this information from Mesos itself.

## Operations

The details of individual Plan commands can be found in the per-service documentation in the Operations section, such as [Cassandra's](https://docs.mesosphere.com/services/cassandra/2.3.0-3.0.16/operations/). Rather than looking at the individual commands, we can instead look into how they work.

Plan operations are effectively reaching into the objects themselves and manually setting their `state` field to a particular value. For example, a `force-complete` call will do the equivalent of `Step.setState(COMPLETE)`. As such, these overrides do not survive across Scheduler restarts, and technically neither do the plans themselves. After a given `state` value has been updated, the change will take effect the next time the Plan's status is checked. In the `force-complete` case, the Phase Strategy will observe that the Step in question appears to be `COMPLETE`, and it will proceed to any other Step(s) in the Phase.

Plan commands should be considered an escape hatch for situations where the Scheduler is doing the wrong thing and needs to be overridden. This can be used to get around a bad situation, but it does not itself automatically resolve the problem that caused the issue to begin with. If used improperly, Plan commands can leave the service in a confused or even broken state.
