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
        config.PACKAGE_NAME,
        install_options["service"]["name"],
        config.DEFAULT_TASK_COUNT,
        additional_options=install_options)
