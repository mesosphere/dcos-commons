PACKAGE_NAME = 'hdfs'
FOLDERED_SERVICE_NAME = '/test/integration/' + PACKAGE_NAME
FOLDERED_SERVICE_AUTOIP_HOST = FOLDERED_SERVICE_NAME.replace('/', '') + '.autoip.dcos.thisdcos.directory'
DEFAULT_TASK_COUNT = 10  # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes

