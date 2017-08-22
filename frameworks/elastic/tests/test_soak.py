import json

import pytest
import sdk_upgrade
from tests import config


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    """ Assumes that the install options file is placed in the repo root directory by the user.
    """
    with open('elastic.json') as options_file:
        install_options = json.load(options_file)
    sdk_upgrade.soak_upgrade_downgrade(
        "beta-{}".format(config.PACKAGE_NAME),
        config.PACKAGE_NAME,
        config.DEFAULT_TASK_COUNT,
        service_name=install_options["service"]["name"],
        additional_options=install_options)
