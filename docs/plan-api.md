# Transactional Plan API

This document covers the usage and design of the transaction plan api (in the package `org.apache.mesos.scheduler.txnplan`).
This API is still extremely alpha!

## Concepts

When writing a distributed system, the first challenge is to containerize your application.
This API helps you after that.
Once you have all these containers, how do you orchestrate them?
How do you express ideas like scale-up logic, recovering from detected failures, and performing routine maintainence?
This API attempts to simplify this task, by allowing developers to write simple operations, compose them together into complex workflows, and hand them off to an automatic, fault-tolerant scheduler, which manages all of the distributed consistency and Mesos-related details.

First, we'll learn how to define individual operations--these can do anything to you system, such as launching tasks, tearing down tasks, invoking AWS APIs, or waiting for a user's input. Next, we'll learn how to compose operations into workflows, each of which we'll call a `Plan`. Finally, we'll learn how to submit a plan for processing.

To enable the Plan API, there's another new concept, called the TaskRegistry.
The registry is the threadsafe clearinghouse house for offers, task status updates, reconciliation, and monitoring tasks--
this allows developers to not need to worry as much about these complex Mesos concepts, as we can implement them correctly once, and be done.

### `Operation`

The fundamental active unit is the `Operation`, an interface with three methods: `doAction`, `rollback`, and `lockedTasks`.
We'll explain what each does in turn, after explaining the basics of the operation.

#### Serializability

Each operation must be serializable--this is one of the ways we ensure consistent fault-tolerant execution of plans.
When writing an operation, we'll serialize it with an enriched instance of `Kryo`, found in `org.apache.mesos.scheduler.txnplan.SerializationUtil`.
You need to make sure that your operation only holds references to small amounts of data that is serializable--that means no sockets, no threads, and no Input/OutputStreams!
Instead, if you need those sorts of resources, I recommend one of several approaches:

If you need a socket, instead store the port/ip the socket connects to in the member variables, and create, use, and destroy the socket entirely within the `doAction`/`rollback` context.

If you need a thread, you may actually need something like a thread pool or other shared, pooled resource.
In this case, make a static pool somewhere that your operation can access.

Finally, if you really need an unserializable object, you may be able to store its creation parameters as normal member variables, and then store the unserializable object as a `transient` member variable.
Then, before you access the object, see if it's null, and if it is, recreate it.
This is advanced Java serialization, and you should try one of the other approaches.

Ever done functional programming?
An operation is a serializable closure that closes over its scope explicitly.

#### Implementation guide

And now, on to the show: what the three methods are for:

`doAction` is the meat of your operation: it will be called when the scheduler decides its time to execute this operation.
If you want to interact with containers, you can do so through the TaskRegistry that you'll be passed.
That registry is capable of creating, destroying, querying, and waiting for specific container statuses.

`rollback` is a convenience function for your SREs--if you hate them, just throw a `RuntimeException` in this method.
When any operation's `doAction` throws an exception, that plan begins to rollback--concurrently running operations are interrupted, and then we begin the rollback process.
As long as no `rollback` of any operation that had completed or was running at the time throws an exception, the scheduler will invoke `rollback` in an inverse, serial order for all the operations that had a chance to run.
This gives each operation a chance to clean up resources or state it may have allocated.
For example, if a plan is attempting to create a new application and associated elastic load balancer, it's nice when you can delete that ELB rather than continuing to pay for it, just because an unrelated issue failed.
Conceptually, `rollback` reflects the idea that many failures are things we expect and understand--we may not care to magically route around a failure, but it's easy to leave the system in a better state than aborting halfway through maintenance.

`lockedTasks` is the final necessary function--this function returns a collection of names of Tasks that this operation will muck around with.
This enables us to have many plans run concurrently if they're doing unrelated things: adding `node3`, `node4`, and `node5`, while a different plan is upgrading `node1` and `node2`.
It's important to be conservative with this method--if you plan to interact with a task in any way (even just querying it!), you should lock it.
Conceptually, tasks should always be in a "good, consistent" state except when they're locked by a plan--this is an analogous idea to monitors in Java.

#### What about `OperationDriver`?

You might've noticed that `doAction` and `rollback` are given an `OperationDriver` as well as a `TaskRegistry`.
We know the `TaskRegistry` is the thread-safe portal into Mesos containers.
The driver is a simple API for operation authors to add checkpointing and logging (and maybe other common functionality in the future) to their operation.
The driver is scoped to the specific operation, to avoid unnecssary boilerplate.
Operations can use `save` and `load` to checkpoint arbitrary state (via Kryo), so that they can easily know exactly how far along they got in their processing, or any assumptions they made at runtime.
`info` and `error` are just stubs for logging, since not everyone's a Java developer familiar with log4j.

## `Plan`

The next feature we'll discuss is the `Plan` API--this is how you'll compose operations into workflows you can submit to the cluster.
To make a plan, we just `new` it:

```java
Plan myPlan = new Plan();
```

Let's suppose we have an operation called `LaunchWebService`, which takes the name of the task it should launch as an argument,
and another operation called `ConfigureServices`, which configures the newly launched services.
When we want to add operations to a plan, we use the `step()` function of our plan--a step is like an instance of an operation that's distributed failure safe:

```java
Step launchWorker1 = myPlan.step(new LaunchWebService("worker-1"));
Step launchWorker2 = myPlan.step(new LaunchWebService("worker-2"));
Step launchWorker3 = myPlan.step(new LaunchWebService("worker-3"));
Step configureWorkers = myPlan.step(new ConfigureServices(Arrays.asList("worker-1", "worker-2", "worker-3")));
```

But wait--how do we ensure that we wait until the workers are launched before we configure them?
You can declare when a step requires another step to finish:

```java
configureWorkers.requires(launchWorker1);
configureWorkers.requires(launchWorker2);
configureWorkers.requires(launchWorker3);
```

That's all there is to defining a `Plan`!
At this point, although we've defined the plan, we haven't submitted it, so a crash at this point would result in nothing happening when the system restarts.
Under the hood, however, there are numerous checks, validations, and globally unique ID augmentations going on to make this easy.
One thing to notice is that a `Plan` is not thread-safe until it's been submitted (aka frozen).
Developers never should need to explicitly freeze a plan--that's automatically done when it's submitted.

## Transactionally submitting plans

The entrypoint to the magic scheduling and checkpointing engine is the `PlanExecutor`.
Once you've managed to create one (not easy yet, see `PlanExecutorTest` for mediocre examples), all you need to do is call `submitPlan` kick things off.
Thus, tying it all together, our plan looks like this:

```java
Plan myPlan = new Plan();

Step launchWorker1 = myPlan.step(new LaunchWebService("worker-1"));
Step launchWorker2 = myPlan.step(new LaunchWebService("worker-2"));
Step launchWorker3 = myPlan.step(new LaunchWebService("worker-3"));
Step configureWorkers = myPlan.step(new ConfigureServices(Arrays.asList("worker-1", "worker-2", "worker-3")));

configureWorkers.requires(launchWorker1);
configureWorkers.requires(launchWorker2);
configureWorkers.requires(launchWorker3);

planExecutor.submitPlan(myPlan);
```

Once `submitPlan` returns, the plan's already been transactionally stored in the backend, and it should run to completion (or rollback).

The `PlanExecutor` has a few other bits of functionality.
By default, it starts in a fresh state, with no plans or data.
If you start submitting plans to it, it will behave as usual.
However, if you decide to call `reloadFromStorage` before submitting any plans, then it will pick up where it left off, by deserializing and recovering its state from storage.
You can still submit new plans after you `reloadFromStorage`, of course!

There's also a listener API for tracking plan progress.
I'm not sure how useful this will actually be thanks to the fault-tolerance guarantees--how do you know what listeners to register after recovery?
For now, it's used for introspection and debugging.
