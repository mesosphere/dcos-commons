from typing import Iterator

import pytest
import sdk_external_volumes
import sdk_security
from tests import config


@pytest.fixture(scope="session")
def configure_security(configure_universe: None) -> Iterator[None]:
    yield from sdk_security.security_session(config.SERVICE_NAME)


@pytest.fixture(scope="session")
def configure_external_volumes():
    # Handle creation of external volumes.
    yield from sdk_external_volumes.external_volumes_session()
