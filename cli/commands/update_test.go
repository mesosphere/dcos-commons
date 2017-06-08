package commands

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
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
		suite.T().Fatal("%s", err)
	}
	suite.requestBody = requestBody

	w.WriteHeader(http.StatusOK)
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
	config.DcosURL = suite.server.URL
}

func (suite *UpdateTestSuite) TearDownTest() {
	suite.capturedOutput.Reset()
	suite.server.Close()
}
func TestUpdateTestSuite(t *testing.T) {
	suite.Run(t, new(UpdateTestSuite))
}

func (suite *UpdateTestSuite) TestDescribe() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/describe.json")
	describe()
	// assert that request contains our app-id
	// TODO
	// assert that printed output is the resolvedOptions field from the JSON
	expectedOutput := suite.loadFile("testdata/output/describe.txt")
	assert.JSONEq(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestDescribeNoOptions() {
	config.Command = "describe"
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/open/describe.json")
	describe()
	// assert that user receives an error message
	expectedOutput := suite.loadFile("testdata/output/no-stored-options.txt")
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdatePackageVersions() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/describe.json")
	printPackageVersions()
	expectedOutput := suite.loadFile("testdata/output/package-versions.txt")
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateConfiguration() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("testdata/input/config.json", "")

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update-configuration.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdatePackageVersion() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("", "stub-universe")

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update-package-version.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateConfigurationAndPackageVersion() {
	suite.responseBody = suite.loadFile("testdata/responses/cosmos/1.10/enterprise/update.json")
	doUpdate("testdata/input/config.json", "stub-universe")

	// assert request is what we expect
	expectedRequest := suite.loadFile("testdata/requests/update.json")
	assert.JSONEq(suite.T(), string(expectedRequest), string(suite.requestBody))

	// assert CLI output is what we expect
	expectedOutput := "Update started. Please use `dcos hello-world --name=hello-world update status` to view progress.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateWithWrongPath() {
	doUpdate("testdata/input/emptyASDF.json", "")
	expectedOutput := "Failed to load specified options file testdata/input/emptyASDF.json: open testdata/input/emptyASDF.json: no such file or directory\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateWithEmptyFile() {
	doUpdate("testdata/input/empty.json", "")
	expectedOutput := "Failed to parse JSON in specified options file testdata/input/empty.json: unexpected end of JSON input\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *UpdateTestSuite) TestUpdateWithMalformedFile() {
	doUpdate("testdata/input/malformed.json", "")
	expectedOutput := "Failed to parse JSON in specified options file testdata/input/malformed.json: unexpected end of JSON input\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}
