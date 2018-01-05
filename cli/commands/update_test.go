package commands

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type UpdateTestSuite struct {
	suite.Suite
	server         *httptest.Server
	requestBody    []byte
	responseBody   []byte
	responseStatus int
	capturedOutput bytes.Buffer
}

func (suite *UpdateTestSuite) printRecorder(format string, a ...interface{}) (n int, err error) {
	suite.capturedOutput.WriteString(fmt.Sprintf(format+"\n", a...))
	return 0, nil // this is probably sub-optimal in the general sense
}

func (suite *UpdateTestSuite) loadFile(filename string) []byte {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		suite.T().Fatal(err)
	}
	return data
}

func (suite *UpdateTestSuite) exampleHandler(w http.ResponseWriter, r *http.Request) {
	// write the request data to our suite's struct
	requestBody, err := ioutil.ReadAll(r.Body)
	if err != nil {
		suite.T().Fatalf("%s", err)
	}
	suite.requestBody = requestBody

	if suite.responseStatus == 0 {
		suite.responseStatus = http.StatusOK
	}
	w.WriteHeader(suite.responseStatus)
	w.Write(suite.responseBody)
}

func (suite *UpdateTestSuite) SetupSuite() {
	config.ModuleName = "hello-world"
	config.ServiceName = "hello-world"

	// reassign printing functions to allow us to check output
	client.PrintMessage = suite.printRecorder
	client.PrintMessageAndExit = suite.printRecorder
}

func (suite *UpdateTestSuite) SetupTest() {
	// set up test server
	suite.server = httptest.NewServer(http.HandlerFunc(suite.exampleHandler))
	os.Setenv("DCOS_URL", suite.server.URL)
	os.Setenv("DCOS_PACKAGE_COSMOS_URL", suite.server.URL)
	os.Setenv("DCOS_ACS_TOKEN", "fake-token")
	os.Setenv("DCOS_SSL_VERIFY", "False")
}

func (suite *UpdateTestSuite) TearDownTest() {
	suite.capturedOutput.Reset()
	suite.server.Close()
	suite.responseStatus = 0
}

func TestUpdateTestSuite(t *testing.T) {
	suite.Run(t, new(UpdateTestSuite))
}

func (suite *UpdateTestSuite) TestDescribe() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/describe.json")
	describe()
	// assert that request contains our appId
	requestBody, err := client.UnmarshalJSON(suite.requestBody)
	if err != nil {
		suite.T().Fatal(err)
	}
	assert.Equal(suite.T(), "hello-world", requestBody["appId"].(string))
	// assert that printed output is the resolvedOptions field from the JSON
	expectedOutput := `{
  "hello": {
    "count": 1,
    "cpus": 0.1,
    "disk": 25,
    "gpus": 1,
    "mem": 252,
    "placement": "hostname:UNIQUE"
  },
  "service": {
    "mesos_api_version": "V1",
    "name": "hello-world",
    "service_account": "",
    "service_account_secret": "",
    "sleep": 1000,
    "spec_file": "svc.yml",
    "user": "root"
  },
  "world": {
    "count": 2,
    "cpus": 0.2,
    "disk": 50,
    "mem": 512,
    "placement": "hostname:UNIQUE"
  }
}
`
	assert.JSONEq(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestDescribeNoOptions() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/open/describe.json")
	describe()
	// assert that user receives an error message
	expectedOutput := `Package configuration is not available for service hello-world.
This command is only available for packages installed with Enterprise DC/OS 1.10 or newer.
`
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdatePackageVersions() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/describe.json")
	printPackageVersions()
	expectedOutput := `Current package version is: "v1.0"
Package can be downgraded to: ["v0.8", "v0.9"]
Package can be upgraded to: ["v1.1", "v2.0"]
`
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdatePackageVersionsNoPackageVersions() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/describe-no-package-versions.json")
	printPackageVersions()
	expectedOutput := `Current package version is: "v1.0"
No valid package downgrade versions.
No valid package upgrade versions.
`
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateConfiguration() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("testdata/input/config.json", "", false)

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update-configuration.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateConfigurationOverwrite() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("testdata/input/config.json", "", true)

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update-configuration-replace.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdatePackageVersion() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("", "stub-universe", false)

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update-package-version.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateConfigurationAndPackageVersion() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("testdata/input/config.json", "stub-universe", false)

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateWithWrongPath() {
	doUpdate("testdata/input/emptyASDF.json", "", false)
	expectedOutput := "Failed to load specified options file testdata/input/emptyASDF.json: open testdata/input/emptyASDF.json: no such file or directory\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateWithEmptyFile() {
	doUpdate("testdata/input/empty.json", "", false)
	expectedOutput := "Failed to parse JSON in specified options file testdata/input/empty.json: unexpected end of JSON input\nContent (1 bytes):  \n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateWithMalformedFile() {
	doUpdate("testdata/input/malformed.json", "", false)
	expectedOutput := fmt.Sprintf("Failed to parse JSON in specified options file testdata/input/malformed.json: unexpected end of JSON input\nContent (340 bytes): %s\n", suite.loadFile("testdata/input/malformed.json"))
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}
