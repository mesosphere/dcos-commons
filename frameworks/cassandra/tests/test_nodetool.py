import pytest
from tests import nodetool

@pytest.mark.sanity
def test_status_parsing():
    status = """Datacenter: datacenter1
=======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address     Load       Tokens       Owns (effective)  Host ID                               Rack
UN  10.0.2.28   74.32 KB   256          62.8%             d71b5d8d-6db1-416e-a25d-4541f06b26bc  us-west-2c
UN  10.0.1.252  75.78 KB   256          67.8%             e848371e-ac01-454a-bb70-f51ed96293a6  us-west-2c
UN  10.0.1.28   83.78 KB   256          69.3%             a553b89d-51e1-4d85-81bb-d1487369082e  us-west-2c

"""

    nodes = nodetool.parse_status(status)
    assert len(nodes) == 3

    test_node = nodes[1]
    assert test_node.get_status() == 'UN'
    assert test_node.get_address() == '10.0.1.252'
    assert test_node.get_load() == '75.78 KB'
    assert test_node.get_tokens() == '256'
    assert test_node.get_owns() == '67.8%'
    assert test_node.get_host_id() == 'e848371e-ac01-454a-bb70-f51ed96293a6'
    assert test_node.get_rack() == 'us-west-2c'
