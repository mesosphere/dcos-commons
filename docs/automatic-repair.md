# Integrating the Automatic Repair Scheduler Feature

dcos-commons provides a framework to add automatic repairs to your scheduler.
Automatic repairs means that we can detect when a container crashes and restart it attached to the same resources if possible, and otherwise start it elsewhere on the cluster.

## Terminology

Here we'll collect some of the terms used throughout the code.

### Stopped task

A task is considered stopped when it first transitions to a terminal state.
Stopped tasks might have undergone a transient issue; because of this, they will be attempted to be restarted in place.
This allows them to use any persistent data they've written to disk--critical for a database!

### Failed task

A task is considered failed when it is definitely not going to be launched again in place.
Failed tasks represent situations where the machine or data is corrupt, and we need to perform heavier-weight recovery.
Failed tasks have lost all their data.

## Usage

The main entrypoint for the automatic repair functionality is the `RepairScheduler`.
Once you create a `RepairScheduler`, you simple call `resourceOffers` whenever you'd like the repairs to run.

You may also create a `RepairController` to expose an API to interact with the RepairScheduler.

### Developer Steps

To integrate the repair scheduler, you'll first need to implement 2 interfaces: `RecoveryRequirementProvider` and `TaskFailureListener`.
These are specific to your framework.

At this point, you'll be able to create a `PlanManager` which implements a repair schedule.
Some users may wish to build custom `LaunchConstrainer`s or `FailureMonitor`s if the included behaviors don't meet your needs.

Most of the parameters to the `RepairScheduler` are self-explanatory.
We'll elaborate on a few of the APIs specific to the `RepairScheduler`.

### `RepairOfferRequirementProvider`

The `RepairOfferRequirementProvider` has 2 methods to create new `OfferRequirement`s to relaunch stopped and failed tasks.

`getReplacementOfferRequirement` takes the terminated task as an argument and returns a new `OfferRequirement` that will relaunch that task using the resources from the terminated task.

`maybeGetNewOfferRequirement` has the flexibility to decide, based on the current `Block`, the current configuration, and any failed tasks its learned of via the `TaskFailureListener` to make an `OfferRequirement` to relaunch a task.
If there's nothing it wants to do, it should return an empty `Optional`.

### `TaskFailureListener`

`TaskFailureListener` is used to notify the framework when a failed task transitions to the failed state.
This allows the framework to do whatever it needs to do in order be able to provider new offer requirements for relaunching the stopped task.

### `FailureMonitor`

The `FailureMonitor` is responsible for determining when a stopped task should transition to the failed state.
Many frameworks may prefer to use the `TimedFailureMonitor`, which allows you to express how many minutes to wait until the task can be trusted to be never coming back.
There's also a `TestingFailureMonitor`, which is useful for writing tests to control the behavior of the repair scheduler.

### `LaunchConstrainer`

The `LaunchConstrainer` is repsonsible for enforcing safety and performance constraints when relaunching stopped & failed tasks.
This way, you can ensure that relaunching doesn't overload the replication mechanisms of the database, or occur at a bad time.
By default, we include a constrainer that does simple rate limiting, and a constrainer designed for making it easy to write tests.
You may wish to write your own constrainers that restrict launches to off-peak times, or observe internal database metrics to decide when it's safe to launch.
