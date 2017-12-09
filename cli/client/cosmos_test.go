package client

import (
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type CosmosTestSuite struct {
	suite.Suite
	server         *httptest.Server
	requestBody    []byte
	responseBody   []byte
	responseStatus int
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

func (suite *CosmosTestSuite) exampleHandler(w http.ResponseWriter, r *http.Request) {
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

func (suite *CosmosTestSuite) SetupSuite() {
	os.Setenv("DCOS_ACS_TOKEN", "dummytoken")

	// reassign printing functions to allow us to check output
	PrintMessage = suite.printRecorder
	PrintMessageAndExit = suite.printRecorder
}

func (suite *CosmosTestSuite) SetupTest() {
	config.ModuleName = "hello-world"
	config.ServiceName = "hello-world"
	// set up test server
	suite.server = httptest.NewServer(http.HandlerFunc(suite.exampleHandler))
	os.Setenv("DCOS_URL", suite.server.URL)
}

func (suite *CosmosTestSuite) TearDownTest() {
	suite.capturedOutput.Reset()
	suite.server.Close()
	suite.responseStatus = 0
}
func TestUpdateTestSuite(t *testing.T) {
	suite.Run(t, new(CosmosTestSuite))
}

func (suite *CosmosTestSuite) createExampleRequest() (*http.Request, []byte) {
	requestBody := `{ "appId" : "my-app" }`
	return createCosmosHTTPJSONRequest("POST", "describe", requestBody), []byte(requestBody)
}

func (suite *CosmosTestSuite) createExampleResponse(statusCode int, filename string) (http.Response, []byte) {
	request, _ := suite.createExampleRequest()
	status := fmt.Sprintf("%v %s", statusCode, http.StatusText(statusCode))

	var responseBody io.ReadCloser
	var fileBytes []byte
	if filename != "" {
		fileBytes = suite.loadFile(filename)
		responseBody = ioutil.NopCloser(bytes.NewBuffer(fileBytes))
	}

	return http.Response{StatusCode: statusCode, Status: status, Request: request, Body: responseBody}, fileBytes
}

func (suite *CosmosTestSuite) Test404ErrorResponse() {
	response, body := suite.createExampleResponse(http.StatusNotFound, "")
	err := checkCosmosHTTPResponse(&response, body)
	assert.Equal(suite.T(), "This command requires Enterprise DC/OS 1.10 or newer.", err.Error())
}

func (suite *CosmosTestSuite) Test500ErrorResponse() {
	response, body := suite.createExampleResponse(
		http.StatusInternalServerError, "testdata/responses/cosmos/1.10/enterprise/marathon-error.json")
	err := checkCosmosHTTPResponse(&response, body)
	assert.Equal(suite.T(), `HTTP POST Query for ` + suite.server.URL + `/cosmos/service/describe failed: 500 Internal Server Error
Response: {"type":"unhandled_exception","message":"java.lang.Error: {\"message\":\"App is locked by one or more deployments. Override with the option '?force=true'. View details at '/v2/deployments/<DEPLOYMENT_ID>'.\",\"deployments\":[{\"id\":\"839314dd-f223-4d55-9d74-a556119e84be\"}]}"}
`, err.Error())
}

func (suite *CosmosTestSuite) test400ErrorResponse(responseBody, outputContains string) {
	response, body := suite.createExampleResponse(http.StatusBadRequest, responseBody)
	err := checkCosmosHTTPResponse(&response, body)
	assert.Contains(suite.T(), err.Error(), outputContains)
}

func (suite *CosmosTestSuite) TestAppNotFoundErrorResponse() {
	config.ServiceName = "hello-world-1"

	// fake 400 response for MarathonAppNotFound
	suite.test400ErrorResponse("testdata/responses/cosmos/1.10/enterprise/bad-name.json",
		`Unable to find the service named 'hello-world-1'.
Possible causes:
- Did you provide the correct service name? Specify a service name with '--name=<name>', or with 'dcos config set hello-world.service_name <name>'.
- Was the service recently installed or updated? It may still be initializing, wait a bit and try again.`)
}

func (suite *CosmosTestSuite) TestBadVersionErrorResponse() {
	// create 400 responses for BadVersionUpdate
	suite.test400ErrorResponse("testdata/responses/cosmos/1.10/enterprise/bad-version.json",
		`Unable to update hello-world to requested version: "not-a-valid"
Valid package versions are: ["v0.8", "v0.9", "v1.1", "v2.0"]`)
	suite.test400ErrorResponse("testdata/responses/cosmos/1.10/enterprise/bad-version-no-versions.json",
		`Unable to update hello-world to requested version: "not-a-valid"
No valid package versions to update to.`)
}

func (suite *CosmosTestSuite) TestJSONMismatchErrorResponse() {
	// create 400 response for JsonSchemaMismatch
	suite.test400ErrorResponse("testdata/responses/cosmos/1.10/enterprise/bad-json.json",
		`Unable to update hello-world to requested configuration: options JSON failed validation.`)
}

func (suite *CosmosTestSuite) TestAppIDChangedErrorResponse() {
	// create 400 response for JsonSchemaMismatch
	suite.test400ErrorResponse("testdata/responses/cosmos/1.10/enterprise/bad-app-id.json",
		`Could not update service name from "/hello-world2" to "/hello-world".
The service name cannot be changed once installed. Ensure service.name is set to "/hello-world2" in options JSON.`)
}

func (suite *CosmosTestSuite) TestGenericErrorResponse() {
	// create 400 response for a generic error
	suite.test400ErrorResponse("testdata/responses/cosmos/1.10/enterprise/generic-error.json",
		`Could not execute command: This an example of a generic Cosmos error response. (GenericErrorType)`)
}

func (suite *CosmosTestSuite) TestCreateCosmosHTTPJSONRequest() {
	// create a request
	request, requestBody := suite.createExampleRequest()

	// check request headers, URL and body
	assert.Equal(suite.T(), "application/vnd.dcos.service.describe-response+json;charset=utf-8;version=v1", request.Header["Accept"][0])
	assert.Equal(suite.T(), "application/vnd.dcos.service.describe-request+json;charset=utf-8;version=v1", request.Header["Content-Type"][0])
	assert.Equal(suite.T(), "token=dummytoken", request.Header["Authorization"][0])
	assert.Equal(suite.T(), suite.server.URL + "/cosmos/service/describe", request.URL.String())
	actualBody, err := ioutil.ReadAll(request.Body)
	if err != nil {
		suite.T().Fatal(err)
	}
	assert.Equal(suite.T(), requestBody, actualBody)
}

func (suite *CosmosTestSuite) TestLocalCosmosUrl() {
	// create a URL where the user has manually specified a URL to Cosmos
	os.Setenv("DCOS_PACKAGE_COSMOS_URL", "https://my.local.cosmos/")

	describeURL := createCosmosURL("describe")
	updateURL := createCosmosURL("update")

	assert.Equal(suite.T(), "https://my.local.cosmos/service/describe", describeURL.String())
	assert.Equal(suite.T(), "https://my.local.cosmos/service/update", updateURL.String())

	os.Unsetenv("DCOS_PACKAGE_COSMOS_URL")
}

func (suite *CosmosTestSuite) TestCosmosUrl() {
	// create a URL where Cosmos is running on the DC/OS cluster
	describeURL := createCosmosURL("describe")
	updateURL := createCosmosURL("update")

	assert.Equal(suite.T(), suite.server.URL + "/cosmos/service/describe", describeURL.String())
	assert.Equal(suite.T(), suite.server.URL + "/cosmos/service/update", updateURL.String())
}

func (suite *CosmosTestSuite) TestInvalidMinimumErrorResponse() {
	responseJSON := `{
    "type": "JsonSchemaMismatch",
    "message": "Options JSON failed validation",
    "data": {
        "errors": [
            {
                "level": "error",
                "schema": {
                    "loadingURI": "#",
                    "pointer": "/properties/nodes/properties/count"
                },
                "instance": {
                    "pointer": "/nodes/count"
                },
                "domain": "validation",
                "keyword": "minimum",
                "message": "numeric instance is lower than the required minimum (minimum: 3, found: 2)",
                "minimum": 3,
                "found": 2
            }
        ]
    }
}`
	suite.responseBody = []byte(responseJSON)
	suite.responseStatus = http.StatusBadRequest

	_, err := HTTPCosmosPostJSON("update", "test-payload")

	// assert CLI output is what we expect
	expectedOutput := "Unable to update hello-world to requested configuration: options JSON failed validation.\n" +
		"\n" +
		"Field        Error \n" +
		"-----        ----- \n" +
		"/nodes/count numeric instance is lower than the required minimum (minimum: 3, found: 2)"

	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *CosmosTestSuite) TestTwoValidationErrorsResponse() {
	responseJSON := `{
    "type": "JsonSchemaMismatch",
    "message": "Options JSON failed validation",
    "data": {
        "errors": [
            {
                "level": "error",
                "schema": {
                    "loadingURI": "#",
                    "pointer": "/properties/nodes/properties/count"
                },
                "instance": {
                    "pointer": "/nodes/count"
                },
                "domain": "validation",
                "keyword": "minimum",
                "message": "numeric instance is lower than the required minimum (minimum: 3, found: 2)",
                "minimum": 3,
                "found": 2
            },
            {
                "level": "error",
                "schema": {
                    "loadingURI": "#",
                    "pointer": "/properties/nodes/properties/cpus"
                },
                "instance": {
                    "pointer": "/nodes/cpus"
                },
                "domain": "validation",
                "keyword": "type",
                "message": "instance type (string) does not match any allowed primitive type (allowed: [\"integer\",\"number\"])",
                "found": "string",
                "expected": [
                    "integer",
                    "number"
                ]
            }
        ]
    }
}
`

	suite.responseBody = []byte(responseJSON)
	suite.responseStatus = http.StatusBadRequest

	_, err := HTTPCosmosPostJSON("update", "test-payload")

	// assert CLI output is what we expect
	expectedOutput := "Unable to update hello-world to requested configuration: options JSON failed validation.\n" +
		"\n" +
		"Field        Error                                                                      \n" +
		"-----        -----                                                                      \n" +
		"/nodes/count numeric instance is lower than the required minimum (minimum: 3, found: 2) \n" +
		"/nodes/cpus  instance type (string) does not match any allowed primitive type (allowed: [\"integer\",\"number\"])"

	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}
