import sdk_tasks

def cmd(pod_name, command):
    return sdk_tasks.task_exec(
        '{}-server'.format(pod_name),
        "bash -c 'JAVA_HOME=$(ls -d jre*/) apache-cassandra-*/bin/nodetool {}'".format(command))

def parse_status(output):
    nodes = []
    lines = _get_status_lines(output)
    for line in _get_status_lines(output):
        node = _parse_status_line(line)
        nodes.append(node)

    return nodes

def _get_status_lines(output):
    raw_lines = output.splitlines()[5:]
    lines = []
    for raw_line in raw_lines:
       if raw_line.strip() != "":
           lines.append(raw_line)

    return lines


def _parse_status_line(line):
    # Input looks like this:
    # UN  10.0.2.28   74.32 KB   256          62.8%             d71b5d8d-6db1-416e-a25d-4541f06b26bc  us-west-2c

    elements = line.split()

    status = elements[0]
    address = elements[1]
    load_size = elements[2]
    load_unit = elements[3]
    tokens = elements[4]
    owns = elements[5]
    host_id = elements[6]
    rack = elements[7]

    # Fix up the load tokens
    load = load_size + " " + load_unit

    return Node(status, address, load, tokens, owns, host_id, rack)


class Node(object):
    def __init__(self, status, address, load, tokens, owns, host_id, rack):
        self.status = status
        self.address = address
        self.load = load
        self.tokens = tokens
        self.owns = owns
        self.host_id = host_id
        self.rack = rack

    def __str__(self):
        return 'status: {}, address: {}, load: {}, tokens: {}, owns: {}, host_id: {}, rack: {}'.format(self.status, self.address, self.load, self.tokens, self.owns, self.host_id, self.rack)

    def get_status(self):
        return self.status

    def get_address(self):
        return self.address

    def get_load(self):
        return self.load

    def get_tokens(self):
        return self.tokens

    def get_owns(self):
        return self.owns

    def get_host_id(self):
        return self.host_id

    def get_rack(self):
        return self.rack
