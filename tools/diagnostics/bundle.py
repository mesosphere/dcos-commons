import json
import logging
import os

log = logging.getLogger(__name__)


class Bundle:
    def write_file(self, file_name, content, serialize_to_json=False):
        """"
        Writes content to a file.

        Please follow the following convention for file names and use underscores rather than dashes:
        * dcos_ prefix for dcos-related outputs
          (e.g. dcos services --json -> dcos_services.json)
        * service_ prefix for service-related outputs
          (e.g. dcos kafka broker list -> service_broker_list.json)
        * $base_tech_ prefix for base-tech-related outputs
          (e.g. dcos task exec $node_0 nodetool status-> cassandra_nodetool_status_0.txt)
        """
        file_path = os.path.join(self.output_directory, file_name)

        with open(file_path, "w") as f:
            log.info("Writing file %s", file_path)
            if serialize_to_json:
                json.dump(content, f, indent=2, sort_keys=True)
            else:
                f.write(content)
                f.write("\n")

    def create(self):
        raise NotImplementedError
