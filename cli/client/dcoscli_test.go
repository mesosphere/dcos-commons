package client

import (
	"os"
	"testing"

	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type DcosCliTestSuite struct {
	suite.Suite
}
func TestDcosCliTestSuite(t *testing.T) {
	suite.Run(t, new(DcosCliTestSuite))
}

func (suite *DcosCliTestSuite) TestMissingDir() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/cliconfig/missing")
	assert.Contains(suite.T(), err.Error(),"no such file or directory")
	assert.Nil(suite.T(), config)
	assert.Nil(suite.T(), cachedConfig)
}

func (suite *DcosCliTestSuite) TestEmptyDir() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/cliconfig/empty")
	assert.Contains(suite.T(), err.Error(),"no such file or directory")
	assert.Nil(suite.T(), config)
	assert.Nil(suite.T(), cachedConfig)
}

func (suite *DcosCliTestSuite) TestNewConfig() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/cliconfig/new")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), config, cachedConfig)

	suite.checkNewConfig(config)
}

func (suite *DcosCliTestSuite) TestTwoAttachedConfigs() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/cliconfig/two-attached")
	assert.Contains(suite.T(), err.Error(), "Multiple configs have an 'attached' flag")
	assert.Nil(suite.T(), config)
	assert.Nil(suite.T(), cachedConfig)
}

func (suite *DcosCliTestSuite) TestPickFromTwoAttachedConfigs() {
	cachedConfig = nil
	config.DcosClusterName = "cluster-1"

	diskConfig, err := cliDiskConfig("testdata/cliconfig/two-attached")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), diskConfig, cachedConfig)

	suite.checkNewConfig(diskConfig)

	config.DcosClusterName = ""
}

func (suite *DcosCliTestSuite) TestBothConfigs() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/cliconfig/both")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), config, cachedConfig)

	suite.checkNewConfig(config)
}

func (suite *DcosCliTestSuite) checkNewConfig(config map[string]interface{}) {
	value, err := cliDiskConfigValue(config, "core.dcos_url")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "http://cluster-1.com/", value)

	value, err = cliDiskConfigValue(config, "core.ssl_verify")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "/path/to/.dcos/clusters/e266cd62-5b75-4b17-87a6-7b29d8ec0df4/dcos_ca.crt", value)

	value, err = cliDiskConfigValue(config, "core.dcos_acs_token")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "cluster-1-token", value)

	value, err = cliDiskConfigValue(config, "package.cosmos_url")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "https://cluster-1-cosmos", value)

	value, err = cliDiskConfigValue(config, "cluster.name")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "cluster-1", value)

	value, err = cliDiskConfigValue(config, "foo")
	assert.Contains(suite.T(), err.Error(),"Unable to tokenize")

	value, err = cliDiskConfigValue(config, "foo.bar")
	assert.Contains(suite.T(), err.Error(),"No section named 'foo'")

	value, err = cliDiskConfigValue(config, "foo.bar.baz")
	assert.Contains(suite.T(), err.Error(),"No section named 'foo'")

	value, err = cliDiskConfigValue(config, "core.foo")
	assert.Equal(suite.T(), "Unable to retrieve value 'foo' from section 'core'", err.Error())

	value, err = cliDiskConfigValue(config, "core.foo.bar")
	assert.Equal(suite.T(), "Unable to retrieve value 'foo.bar' from section 'core'", err.Error())
}

func (suite *DcosCliTestSuite) TestOldConfig() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/cliconfig/old")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), config, cachedConfig)

	suite.checkOldConfig(config)
}

func (suite *DcosCliTestSuite) TestOldConfigCustomPath() {
	cachedConfig = nil

	config.DcosConfigPath = "testdata/cliconfig/old/dcos.toml"

	diskConfig, err := cliDiskConfig("path/doesnt/exist")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), diskConfig, cachedConfig)

	config.DcosConfigPath = ""

	suite.checkOldConfig(diskConfig)
}

func (suite *DcosCliTestSuite) checkOldConfig(config map[string]interface{}) {
	value, err := cliDiskConfigValue(config, "core.dcos_url")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "http://old-cluster.com/", value)

	value, err = cliDiskConfigValue(config, "core.ssl_verify")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "false", value)

	value, err = cliDiskConfigValue(config, "core.dcos_acs_token")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "old-cluster-token", value)

	value, err = cliDiskConfigValue(config, "package.cosmos_url")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "https://old-cluster-cosmos", value)

	value, err = cliDiskConfigValue(config, "cluster.name")
	assert.Contains(suite.T(), err.Error(),"No section named 'cluster'")

	value, err = cliDiskConfigValue(config, "foo")
	assert.Contains(suite.T(), err.Error(),"Unable to tokenize")

	value, err = cliDiskConfigValue(config, "foo.bar")
	assert.Contains(suite.T(), err.Error(),"No section named 'foo'")

	value, err = cliDiskConfigValue(config, "foo.bar.baz")
	assert.Contains(suite.T(), err.Error(),"No section named 'foo'")

	value, err = cliDiskConfigValue(config, "core.foo")
	assert.Equal(suite.T(), "Unable to retrieve value 'foo' from section 'core'", err.Error())

	value, err = cliDiskConfigValue(config, "core.foo.bar")
	assert.Equal(suite.T(), "Unable to retrieve value 'foo.bar' from section 'core'", err.Error())
}

func (suite *DcosCliTestSuite) TestEnvvarOverrides() {
	cachedConfig = nil

	suite.checkEnvOverride("DCOS_FOO_BAR", "core.dcos_foo_bar")
	suite.checkEnvOverride("DCOS_DCOS_FOO_BAR", "core.dcos_foo_bar")
	suite.checkEnvOverride("DCOS_FOO_BAR", "core.foo_bar")
	suite.checkEnvOverride("DCOS_OTHER_FOO_BAR", "other.foo_bar")

	suite.checkEnvOverride("DCOS_URL", "core.dcos_url")
	suite.checkEnvOverride("DCOS_DCOS_URL", "core.dcos_url")

	suite.checkEnvOverride("DCOS_ACS_TOKEN", "core.dcos_acs_token")
	suite.checkEnvOverride("DCOS_DCOS_ACS_TOKEN", "core.dcos_acs_token")

	suite.checkEnvOverride("DCOS_SSL_VERIFY", "core.ssl_verify")

	suite.checkEnvOverride("DCOS_KAFKA_SERVICE_NAME", "kafka.service_name")
	suite.checkEnvOverride("DCOS_CLUSTER_NAME", "cluster.name")
}

func (suite *DcosCliTestSuite) checkEnvOverride(envName, configName string) {
	const testVal = "test-value"
	os.Setenv(envName, testVal)
	assert.Equal(suite.T(), cliEnvConfigValue(configName), testVal)
	os.Unsetenv(envName)
}
