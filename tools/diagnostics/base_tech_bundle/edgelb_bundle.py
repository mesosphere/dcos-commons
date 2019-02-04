import logging

import sdk_cmd

from base_tech_bundle import BaseTechBundle
import config
import json

logging = logging.getLogger(__name__)


class EdgeLBBundle(BaseTechBundle):
    def __init__(self, package_name, service_name, scheduler_tasks, service, output_directory):
        # Override package name as 'edgelb' rather than 'edgelb-pool'
        if package_name != 'edgelb':
            package_name = 'edgelb'

        # Override service name as 'edgelb' rather than 'dcos-edgelb/pools/<pool-name>'
        if service_name != 'edgelb':
            service_name = 'edgelb'

        super().__init__(package_name,
                         service_name,
                         scheduler_tasks,
                         service,
                         output_directory)

    @config.retry
    def create_version_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "version", print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not get service version. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal service version message\nstderr: '%s'", stderr)
            self.write_file("edgelb_version", stdout)

    @config.retry
    def create_ping_file(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "ping", print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not ping service. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal service ping message\nstderr: '%s'", stderr)
            self.write_file("edgelb_ping", stdout)

    @config.retry
    def create_pool_status_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "status {} --json".format(pool_name),
            print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not generate status for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal status {} message\nstderr: '%s'".format(pool_name),
                                stderr)
            self.write_file("edgelb_status_{}.json".format(pool_name), stdout)

    @config.retry
    def create_pool_endpoints_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "endpoints {} --json".format(pool_name),
            print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not generate endpoints for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal endpoints {} message\nstderr: '%s'".format(pool_name),
                                stderr)
            self.write_file("edgelb_endpoints_{}.json".format(pool_name), stdout)

    @config.retry
    def create_pool_lb_config_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "lb-config {}".format(pool_name),
            print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not generate lb-config for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal lb-config {} message\nstderr: '%s'".format(pool_name),
                                stderr)
            self.write_file("edgelb_lb-config_{}".format(pool_name), stdout)

    @config.retry
    def create_pool_lb_template_file(self, pool):
        pool_name = pool['name']
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "template show {}".format(pool_name),
            print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not generate template show for pool {}. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'".format(pool_name), rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal template show {} message\nstderr: '%s'".format(pool_name),
                                stderr)
            self.write_file("edgelb_template_show_{}".format(pool_name), stdout)

    @config.retry
    def create_pool_files(self):
        rc, stdout, stderr = sdk_cmd.svc_cli(
            self.package_name, self.service_name, "list --json", print_output=False
        )

        if rc != 0:
            logging.error(
                "Could not generate pool list. return-code: '%s'\n"
                "stdout: '%s'\nstderr: '%s'", rc, stdout, stderr
            )
        else:
            if stderr:
                logging.warning("Non-fatal pool list message\nstderr: '%s'", stderr)

            try:
                pools = json.loads(stdout)
                # Write out full pools list.
                self.write_file("edgelb_pools_list.json", stdout)
                # Issue pool specific command.
                for pool in pools:
                    self.create_pool_status_file(pool)
                    self.create_pool_endpoints_file(pool)
                    self.create_pool_lb_config_file(pool)
                    self.create_pool_lb_template_file(pool)
            except Exception:
                logging.error(
                    "Could not parse pool list json.\nstdout: '%s'\nstderr: '%s'",
                    stdout, stderr
                )

    def create(self):
        logging.info("Creating EdgeLB Bundle")
        self.create_version_file()
        self.create_ping_file()
        self.create_pool_files()
