#!/usr/bin/env python3
#
# TODO: usage, description.

import json
import logging
import os
import os.path
import sys
from datetime import date, datetime

import sdk_cmd
import sdk_tasks

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(message)s")


class Bundle(object):
    def __init__(self, package_name, service_name, directory_name):
        self.package_name = package_name
        self.service_name = service_name
        self.directory_name = directory_name

    def write_file(self, file_name, content, is_json=True):
        file_path = os.path.join(self.directory_name, file_name)

        with open(file_path, 'w') as f:
            logger.info('Writing file {}'.format(file_path))
            if is_json:
                json.dump(content, f, indent=2, sort_keys=True)
            else:
                f.write(content)

    def create(self):
        raise NotImplementedError


class BaseTechBundle(Bundle):
    def for_each_task(self, task_prefix, fn):
        task_ids = sdk_tasks.get_task_ids(self.service_name, task_prefix)

        for task_id in task_ids:
            fn(task_id)


class CassandraBundle(BaseTechBundle):
    def task_exec(self, task_id, cmd):
        full_cmd = " ".join([
            'export JAVA_HOME=$(ls -d ${MESOS_SANDBOX}/jdk*/jre/) &&',
            'export TASK_IP=$(${MESOS_SANDBOX}/bootstrap --get-task-ip) &&',
            'CASSANDRA_DIRECTORY=$(ls -d ${MESOS_SANDBOX}/apache-cassandra-*/) &&',
            cmd
        ])

        return sdk_cmd.marathon_task_exec(task_id,
                                          'bash -c \'{}\''.format(full_cmd))

    def create_nodetool_status_file(self, task_id):
        rc, stdout, stderr = self.task_exec(
            task_id, '${CASSANDRA_DIRECTORY}/bin/nodetool status')

        self.write_file(
            'cassandra_nodetool_status_{}.txt'.format(task_id),
            stdout,
            is_json=False)

    def create_nodetool_tpstats_file(self, task_id):
        rc, stdout, stderr = self.task_exec(
            task_id, '${CASSANDRA_DIRECTORY}/bin/nodetool tpstats')

        self.write_file(
            'cassandra_nodetool_tpstats_{}.txt'.format(task_id),
            stdout,
            is_json=False)

    def create_tasks_nodetool_status_files(self):
        self.for_each_task('node', self.create_nodetool_status_file)

    def create_tasks_nodetool_tpstats_files(self):
        self.for_each_task('node', self.create_nodetool_tpstats_file)

    def create(self):
        logger.info('Creating Cassandra bundle')
        self.create_tasks_nodetool_status_files()
        self.create_tasks_nodetool_tpstats_files()


class ElasticBundle(BaseTechBundle):
    def create(self):
        logger.info('Creating Elastic bundle')


class HdfsBundle(BaseTechBundle):
    def create(self):
        logger.info('Creating HDFS bundle')


class KafkaBundle(BaseTechBundle):
    def create(self):
        logger.info('Creating Kafka bundle')


BASE_TECH_BUNDLE = {
    'beta-cassandra': CassandraBundle,
    'beta-elastic': ElasticBundle,
    'beta-hdfs': HdfsBundle,
    'beta-kafka': KafkaBundle,
    'cassandra': CassandraBundle,
    'elastic': ElasticBundle,
    'hdfs': HdfsBundle,
    'kafka': KafkaBundle,
}


class ServiceBundle(Bundle):
    def create_task_file(self):
        output = sdk_cmd.get_json_output('task --json', print_output=False)
        self.write_file('dcos_task.json', output)

    def create_marathon_task_list_file(self):
        output = sdk_cmd.get_json_output(
            'marathon task list --json', print_output=False)
        self.write_file('dcos_marathon_task_list.json', output)

    def create_service_configuration_file(self):
        output = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            'describe',
            json=True,
            print_output=False)

        self.write_file('service_configuration.json', output)

    def create_service_pod_status_file(self):
        output = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            'pod status --json',
            json=True,
            print_output=False)

        self.write_file('service_pod_status.json', output)

    def create_plan_status_file(self, plan):
        output = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            'plan status {} --json'.format(plan),
            json=True,
            print_output=False)

        self.write_file('service_plan_status_{}.json'.format(plan), output)

    def create_plans_status_files(self):
        plans = sdk_cmd.svc_cli(
            self.package_name,
            self.service_name,
            'plan list',
            json=True,
            print_output=False)

        for plan in plans:
            self.create_plan_status_file(plan)

    def create(self):
        self.create_task_file()
        self.create_marathon_task_list_file()
        self.create_service_configuration_file()
        self.create_service_pod_status_file()
        self.create_plans_status_files()
        BASE_TECH_BUNDLE[self.service_name](self.package_name,
                                            self.service_name,
                                            self.directory_name).create()


def print_usage(argv):
    logger.info('TODO: usage')


def main(argv):
    if len(argv) < 2:
        print_usage(argv)
        return 1

    package_name = argv[1]
    service_name = argv[2]

    def directory_date_string():
        return date.strftime(datetime.now(), '%Y%m%d%S')

    def directory_name(package_name, service_name):
        return '{}_{}_{}'.format(package_name, service_name,
                                 directory_date_string())

    def create_directory(package_name, service_name):
        _directory_name = directory_name(package_name, service_name)

        if not os.path.exists(_directory_name):
            logger.info('Creating directory {}'.format(_directory_name))
            os.makedirs(_directory_name)

        return _directory_name

    ServiceBundle(package_name, service_name,
                  create_directory(package_name, service_name)).create()

    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
