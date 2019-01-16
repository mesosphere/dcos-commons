# CHANGELOG

## v0.2.0 (2019-01-16) - Snowman

### Bug fixes
   - Fixes `IndexError: list index out of range` exception when it can't find an
     active scheduler in the Mesos state
     ([commit](https://github.com/mesosphere/dcos-commons/commit/155a6dfbd6b5e6fdfcdcd31d030942be92f53f4c))

### Improvements
   - Add kafka-specific steps to fetch broker info, and run them for
     confluent-kafka as well
     ([commit](https://github.com/mesosphere/dcos-commons/commit/74872003047ac0ee515d74a82987c90bf0e6ce07))

## v0.1.0 (2018-10-02) - Initial release
