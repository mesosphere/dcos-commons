import json
import logging
import os

log = logging.getLogger(__name__)


class Bundle:
    def write_file(self, file_name, content, serialize_to_json=False):
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
