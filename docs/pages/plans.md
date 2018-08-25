---
title: Plans
menuWeight: 1
toc: true
---

Plans are how SDK Services convey progress through service management operations, such as repairing failed tasks and/or rolling out changes to the service's configuration. This document describes what Plans are, how they work, and how they can be used in the context of running a service as an operator or building a new service as a developer.

For more information about customizing Plans in a service that's under development, see the [developer guide](../developer-guide/#plans).

## Overview

Each Plan is a tree with a fixed three-level hierarchy of the Plan itself, its Phases, and then Steps within those Phases. These are all collectively referred to as "Elements". The choice of three levels was arbitrarily chosen as "enough levels for anybody". The fixed tree hierarchy was chosen in order to simplify building UIs that display plan content. In particular, lots of suggestions were made to have a full DAG structure, which were ultimately rejected. This three-level hierarchy can look as follows:

Plan foo
├─ Phase bar
│  ├─ Step qux
│  └─ Step quux
└─ Phase baz
   ├─ Step quuz
   ├─ Step corge
   └─ Step grault

Plans are advertised by the Scheduler via `/v1/plans` endpoints. In practice, most (but not all) work performed by the Scheduler is conveyed via Plans. A given service can have multiple Plans, all doing work in parallel. This work can include deploying the service, rolling out configuration changes or upgrades, relaunching failed or replaced tasks, decommissioning pods, and uninstalling the service. The developer can even define their own custom plans to be manually invoked by the operator. If the Scheduler is running [multiple services](../multi-service/), then each of those services will have its own set of Plans, accessible at `/v1/multi/<svcname>/plans`.

Plans as a concept have several benefits:
- They allow operators to see what the service is currently doing, and to visualize the broader operation such as for a config rollout. The fixed structure of that information meanwhile makes it straightforward to build UIs and tooling on top.
- They allow operators to assign the state of running operations via editing the state of Plan elements. For example, a stuck uninstall could be updated to stop waiting to clean up resources that no longer exist in the cluster.
- They allow developers to customize how common operations are performed, or to define new custom operations that can be triggered by end-users. For more information on this, see the [developer guide](../developer-guide/#plans).

## Statuses

TODO

The Plan has a Status value such as COMPLETE

plan derived from phases, phase derived from steps

[Status reference](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/plan/Status.java)
[child status logic](https://github.com/mesosphere/dcos-commons/blob/4910aeb/sdk/scheduler/src/main/java/com/mesosphere/sdk/scheduler/plan/PlanUtils.java#L93)

## Strategies

When deciding what work to do next, the Scheduler polls the Plan for a list of Candidate Steps. Candidate Steps are the steps which are due to be processed. The Plan uses its assigned Strategy to pick the candidate Phases which then use their respective Strategies to decide their candidate Steps. The Scheduler then attempts to process the list of Candidate Steps that it got back.

These Strategies are effectively implementations of some dependency structure, based on the statuses of the elements they're wrapping. For example, a parallel Strategy would return all elements that haven't been completed yet, while a serial Strategy would only return the next incomplete element in some ordered sequence. For example, the following Plan shows a mix of serial and parallel strategies:

foo (serial strategy) (IN\_PROGRESS)
├─ bar (serial strategy) (COMPLETE)
│  ├─ Step qux (COMPLETE)
│  └─ Step quux (COMPLETE)
└─ baz (parallel strategy) (IN\_PROGRESS)
   ├─ Step quuz (STARTING)
   ├─ Step corge (PENDING)
   └─ Step grault (COMPLETE)

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

### Plan parallelism

By default, services come with two plans up-front. One is the `deploy` plan, which deals with rolling out changes to the service's configuration, and the other is the `recovery` plan, which automatically recovers tasks that have exited or been restarted by the user. If pods are being decommissioned, a third plan named `decommission` will also be added.

These plans will all run in parallel, where Steps from each plan are fetched and then executed in a single pass. However, some of these Steps may be doing work on the same underlying object, e.g. one Step may be trying to update a given pod, while another may be trying to tear it down. To avoid contention, we have the concept of TODO NAME?. If two Steps share the same TODO NAME then only one of them will be worked on at a time.

But why would we want these to run in parallel in the first place? The main reason is so that work in one plan isn't necessarily blocked waiting on work in another plan. For example, there isn't any reason that the Scheduler can't be updating pod-2 while it also recovers pod-4.

### Running Steps

TODO explain how scheduler executes the step and it's got java code inside

### Updating Steps

TODO explain how scheduler notifies the step of offer outcomes and task statuses

## Use cases

Now that we have a basic understanding of how the Plans are structured, how are they put to practice? Lets look at some common scenarios and see how they behave.

TODO:
- initial deployment
  - stuck readiness check explanation in particular
- config change
  - partial redeploy: detecting matching config
  - interaction of config change vs repair (relaunch doesn't change config)
- pod restart
- pod replace (restart + permanently failed bit marking existing task as 'forgotten')
- uninstall
  - stuck uninstall in particular

deploy (serial strategy) (IN_PROGRESS)
├─ hello (serial strategy) (COMPLETE)
│  └─ hello-0:[server] (COMPLETE)
└─ world (serial strategy) (STARTING)
   ├─ world-0:[server] (STARTING)
   └─ world-1:[server] (PENDING)

## Operations

TODO

describe force-complete etc, show examples of outcomes
