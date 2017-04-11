

In version 1.1.20, we utilize dcos-commons SDK to create a Kafka framework. Please see ‘src/main/dist/svc.yml’ for more information on how Kafka service is defined. 


* config.json changes in version 1.1.20

 - ‘placement_constraints’:  Marathon style placement constraints. “hostname:MAX_PER:1” is equal to NODE placement_strategy in version 1.1.19

 - ‘deploy_strategy’: Available strategies are serial, serial-canary, parallel-canary, parallel. INSTALL in version 1.1.19 is same as ‘serial’ strategy. STAGE in version 1.1.19 is same as ‘serial-canary’ strategy.

Kafka service has one plan, called ‘deploy’. In this deploy plan, there is a phase, called ‘Deployment’. Deployment phase has multiple steps, representing Kafka broker tasks.

 - JMX and STATSD options are deprecated.

 - Kafka Zookeeper uri is not configurable, it is set to {MESOS_ZOOKEEPER_URI}/dcos-service-{FRAMEWORK_NAME} 




* Upgrade instructions from dcos-kafka-service:1.1.19 to dcos-kafka-service:1.1.20

dcos-kafka-service keeps its current state including task information and active configuration in Zookeeper. The format of this framework state has changed in version 1.1.20. Here are two different approach to upgrade from version 1.1.19 to 1.1.20:

- Auto upgrade:

dcos-kafka-service 1.1.20 comes with an auto upgrade feature. It automatically detects an existing old state format, stored in Zookeeper. It creates a transient state store in new format with new task information and configuration information, so yaml-based SDK framework can resume execution.

In order to proceed with auto upgrade, existing framework should have all tasks running, no failure or error, and all tasks should have reached their target configurations. Otherwise, auto upgrade will abort itself. Before upgrading your framework from 1.19 to 1.20, run dcos kafka CLI and make sure plans/phases/steps are all completed without errors.

Zookeeper path for task info is dcos-service-kafka/TaskInfo, and dcos-service-kafka/Configuration for configuration.  You can roll back if auto upgrade fails; it will not change framework state if there is an error in the auto-upgrade process.

Task names in new format starts with “kafka” [kafka-([0-9]+)-broker]. By default, auto upgrade keeps backup of old task info. Old task names starts with “broker” [broker-[0-9]+]. It can be configured to be deleted at the end of the process). 

If CONFIG_UPGRADE_CLEANUP environment variable is set (to any value) in Marathon, auto upgrade will delete all backups at the end. If CONFIG_UPGRADE_DISABLE is set, auto upgrade will be disabled. Please see Marathon Environment Variables.

- Backup/Restore

You can create a new Kafka service running version 1.1.20, and use available Kafka tool to migrate data from old Kafka service. “kafka.tools.MirrorMaker” and “kafka.tools.MigrationTool” are commons tools for backup/restore or mirroring after major upgrades in Kafka.



