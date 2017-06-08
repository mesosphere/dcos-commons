package client

import (
	"bytes"
	"io"
	"net/http"
	"testing"

	"io/ioutil"

	"fmt"

	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type CosmosTestSuite struct {
	suite.Suite
	capturedOutput bytes.Buffer
}

func (suite *CosmosTestSuite) printRecorder(format string, a ...interface{}) (n int, err error) {
	suite.capturedOutput.WriteString(fmt.Sprintf(format+"\n", a...))
	return 0, nil // this is probably sub-optimal in the general sense
}

func (suite *CosmosTestSuite) loadFile(filename string) []byte {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		suite.T().Fatal(err)
	}
	return data
}

func (suite *CosmosTestSuite) SetupSuite() {
	config.DcosURL = "https://my.dcos.url/"
	config.DcosAuthToken = "dummytoken"

	// reassign printing functions to allow us to check output
	PrintMessage = suite.printRecorder
	PrintMessageAndExit = suite.printRecorder
}

func (suite *CosmosTestSuite) SetupTest() {
	config.ModuleName = "hello-world"
	config.ServiceName = "hello-world"
}

func (suite *CosmosTestSuite) TearDownTest() {
	suite.capturedOutput.Reset()
}
func TestUpdateTestSuite(t *testing.T) {
	suite.Run(t, new(CosmosTestSuite))
}

func (suite *CosmosTestSuite) createExampleRequest() (*http.Request, []byte) {
	requestBody := suite.loadFile("testdata/requests/example.json")
	return createCosmosHTTPJSONRequest("POST", "describe", string(requestBody)), requestBody
}

func (suite *CosmosTestSuite) createExampleResponse(statusCode int, filename string) http.Response {
	request, _ := suite.createExampleRequest()
	status := fmt.Sprintf("%v %s", statusCode, http.StatusText(statusCode))

	var responseBody io.ReadCloser
	if filename != "" {
		responseBody = ioutil.NopCloser(bytes.NewBuffer(suite.loadFile(filename)))
	}

	return http.Response{StatusCode: statusCode, Status: status, Request: request, Body: responseBody}
}

func (suite *CosmosTestSuite) Test404ErrorResponse() {
	config.Command = "describe"

	// fake 404 response
	fourOhFourResponse := suite.createExampleResponse(http.StatusNotFound, "")

	checkCosmosHTTPResponse(&fourOhFourResponse)

	expectedOutput := suite.loadFile("testdata/output/404.txt")
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *CosmosTestSuite) TestAppNotFoundErrorResponse() {
	config.ServiceName = "hello-world-1"

	// fake 400 response for MarathonAppNotFound
	fourHundredResponse := suite.createExampleResponse(http.StatusBadRequest, "testdata/responses/cosmos/1.10/enterprise/bad-name.json")

	checkCosmosHTTPResponse(&fourHundredResponse)

	expectedOutput := suite.loadFile("testdata/output/bad-name.txt")
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *CosmosTestSuite) TestBadVersionErrorResponse() {
	// create 400 response for BadVersionUpdate
	fourHundredResponse := suite.createExampleResponse(http.StatusBadRequest, "testdata/responses/cosmos/1.10/enterprise/bad-version.json")

	checkCosmosHTTPResponse(&fourHundredResponse)

	expectedOutput := suite.loadFile("testdata/output/bad-version.txt")
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}
func (suite *CosmosTestSuite) TestCreateCosmosHTTPJSONRequest() {
	// create a request
	request, requestBody := suite.createExampleRequest()

	// check request headers, URL and body
	assert.Equal(suite.T(), "application/vnd.dcos.service.describe-response+json;charset=utf-8;version=v1", request.Header["Accept"][0])
	assert.Equal(suite.T(), "application/vnd.dcos.service.describe-request+json;charset=utf-8;version=v1", request.Header["Content-Type"][0])
	assert.Equal(suite.T(), "token=dummytoken", request.Header["Authorization"][0])
	assert.Equal(suite.T(), "https://my.dcos.url/cosmos/service/describe", request.URL.String())
	actualBody, err := ioutil.ReadAll(request.Body)
	if err != nil {
		suite.T().Fatal(err)
	}
	assert.Equal(suite.T(), requestBody, actualBody)
}

func (suite *CosmosTestSuite) TestLocalCosmosUrl() {
	// create a URL where the user has manually specified a URL to Cosmos
	config.CosmosURL = "https://my.local.cosmos/"

	describeURL := createCosmosURL("describe")
	updateURL := createCosmosURL("update")

	assert.Equal(suite.T(), "https://my.local.cosmos/service/describe", describeURL.String())
	assert.Equal(suite.T(), "https://my.local.cosmos/service/update", updateURL.String())

	config.CosmosURL = ""
}

func (suite *CosmosTestSuite) TestCosmosUrl() {
	// create a URL where Cosmos is running on the DC/OS cluster
	describeURL := createCosmosURL("describe")
	updateURL := createCosmosURL("update")

	assert.Equal(suite.T(), "https://my.dcos.url/cosmos/service/describe", describeURL.String())
	assert.Equal(suite.T(), "https://my.dcos.url/cosmos/service/update", updateURL.String())
}
