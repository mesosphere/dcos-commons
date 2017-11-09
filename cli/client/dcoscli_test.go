package client

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type DcosCliTestSuite struct {
	suite.Suite
}
func TestDcosCliTestSuite(t *testing.T) {
	suite.Run(t, new(DcosCliTestSuite))
}

func (suite *DcosCliTestSuite) TestGetCLIValues() {
	cachedConfig = nil

	config, err := cliDiskConfig("testdata/config_missing")
	assert.Equal(suite.T(), "open testdata/config_missing/.dcos/clusters: no such file or directory", err.Error())
	assert.Nil(suite.T(), config)
	assert.Nil(suite.T(), cachedConfig)

	config, err = cliDiskConfig("testdata/config")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), config, cachedConfig)

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
	assert.Equal(suite.T(), "Unable to tokenize config value into 'section.name': foo => [foo]", err.Error())

	value, err = cliDiskConfigValue(config, "foo.bar")
	assert.Equal(suite.T(), "No section named 'foo' when fetching 'foo.bar'", err.Error())

	value, err = cliDiskConfigValue(config, "foo.bar.baz")
	assert.Equal(suite.T(), "No section named 'foo' when fetching 'foo.bar.baz'", err.Error())

	value, err = cliDiskConfigValue(config, "core.foo")
	assert.Equal(suite.T(), "Unable to retrieve value 'foo' from section 'core'", err.Error())

	value, err = cliDiskConfigValue(config, "core.foo.bar")
	assert.Equal(suite.T(), "Unable to retrieve value 'foo.bar' from section 'core'", err.Error())
}
