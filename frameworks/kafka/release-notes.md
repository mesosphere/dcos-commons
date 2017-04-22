---
post_title: Release Notes
menu_order: 90
feature_maturity: preview
enterprise: 'no'
---

DC/OS Kafka version 1.1.20 is built using the DC/OS Commons SDK. See `src/main/dist/svc.yml` for more information on how the service is defined.

# Changes in `config.json`

 - `placement_constraints`:  Marathon style placement constraints. `hostname:MAX_PER:1` is equal to `NODE placement_strategy` in DC/OS Kafka version 1.1.19

 - `deploy_strategy`: Available strategies are serial, serial-canary, parallel-canary, and parallel. `INSTALL` in version 1.1.19 is same as ‘serial’ strategy. `STAGE` in version 1.1.19 is same as the ‘serial-canary’ strategy.

# Plans
DC/OS Kafka has one plan, called ‘deploy’. This plan has a  ‘Deployment’ phase with multiple steps that represent Kafka broker tasks.

# Other Changes
 
 - JMX and STATSD options are deprecated.

 - The Kafka ZooKeeper URI is not configurable. It is set to `{MESOS_ZOOKEEPER_URI}/dcos-service-{FRAMEWORK_NAME}`
 
# Upgrade Instructions from Version 1.1.19 to 1.1.20

DC/OS Kafka keeps its current state, including task information and active configuration, in ZooKeeper. The format of this framework state has changed in version 1.1.20. You can perform an auto upgrade or perform a backup and restore operation to upgrade.

## Auto upgrade

DC/OS Kafka version 1.1.20 comes with an auto upgrade feature. It automatically detects an existing old state format stored in ZooKeeper. Then, it creates a transient state store in the new format with the new task and configuration information so that the yaml-based SDK framework can resume execution.

In order to perform auto upgrade, the existing framework must have all tasks running, no failures or errors, and all tasks must have reached their target configurations. Otherwise, auto upgrade will abort itself. Before upgrading your framework from 1.19 to 1.20, run `dcos kafka plan` from the DC/OS CLI to make sure plans/phases/steps are all completed without errors.

The ZooKeeper path for task info is `dcos-service-kafka/TaskInfo`. The path for configuration is `dcos-service-kafka/Configuration`. You can roll back if auto upgrade fails; framework state will not change if there is an error in the auto upgrade process.

In the new format, task names start with “kafka” (`[kafka-([0-9]+)-broker]`). By default, auto upgrade keeps a backup of old task info. Old task names start with “broker” (`[broker-[0-9]+]`).

You can configure auto upgrade to delete the old task info when the upgrade process is complete. If the `CONFIG_UPGRADE_CLEANUP` environment variable is set (to any value), auto upgrade will delete all backups at the end. If `CONFIG_UPGRADE_DISABLE` is set, auto upgrade will be disabled. To set these variables, go to **Services** > **Services** > **Kafka** in the DC/OS web interface. Then, click the menu in the upper right hand corner (three dots) and choose **Edit**. In the window that appears, click the **Environment** tab and then **+ ADD ENVIRONMENT VARIABLE**.

## Backup/Restore

Create a new Kafka service running version 1.1.20. Then, use a Kafka tool to migrate your data from the old Kafka service. `kafka.tools.MirrorMaker` and `kafka.tools.MigrationTool` are commons tools for performing backup/restore or mirroring after major upgrades in Kafka.



