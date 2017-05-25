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
)

var capturedOutput bytes.Buffer

func logRecorder(format string, a ...interface{}) {
	capturedOutput.WriteString(fmt.Sprintf(format+"\n", a...))
}

func loadFile(t *testing.T, filename string) []byte {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func createExampleRequest(t *testing.T) (*http.Request, []byte) {
	requestBody := loadFile(t, "testdata/requests/example.json")
	return createCosmosHTTPJSONRequest("POST", "describe", string(requestBody)), requestBody
}

func createExampleResponse(t *testing.T, statusCode int, filename string) http.Response {
	request, _ := createExampleRequest(t)
	status := fmt.Sprintf("%v %s", statusCode, http.StatusText(statusCode))

	var responseBody io.ReadCloser
	if filename != "" {
		responseBody = ioutil.NopCloser(bytes.NewBuffer(loadFile(t, filename)))
	}

	return http.Response{StatusCode: statusCode, Status: status, Request: request, Body: responseBody}
}

func setup() {
	config.DcosUrl = "https://my.dcos.url/"
	config.DcosAuthToken = "dummytoken"
	config.ServiceName = "hello-world"

	// reassign logging functions to allow us to check output
	LogMessage = logRecorder
	LogMessageAndExit = logRecorder
}

func teardown() {
	capturedOutput.Reset()
}
func Test404ErrorResponse(t *testing.T) {
	setup()

	// fake 404 response
	fourOhFourResponse := createExampleResponse(t, http.StatusNotFound, "")

	checkCosmosHTTPResponse(&fourOhFourResponse)

	expectedOutput := loadFile(t, "testdata/output/404.txt")
	assert.Equal(t, string(expectedOutput), capturedOutput.String())

	teardown()
}

func TestAppNotFoundErrorResponse(t *testing.T) {
	setup()

	config.ServiceName = "hello-world-1"

	// fake 400 response for MarathonAppNotFound
	fourHundredResponse := createExampleResponse(t, http.StatusBadRequest, "testdata/responses/cosmos/1.10/enterprise/bad-name.json")

	checkCosmosHTTPResponse(&fourHundredResponse)

	expectedOutput := loadFile(t, "testdata/output/bad-name.txt")
	assert.Equal(t, string(expectedOutput), capturedOutput.String())

	teardown()
}

func TestBadVersionErrorResponse(t *testing.T) {
	setup()

	// create 400 response for BadVersionUpdate
	fourHundredResponse := createExampleResponse(t, http.StatusBadRequest, "testdata/responses/cosmos/1.10/enterprise/bad-version.json")

	checkCosmosHTTPResponse(&fourHundredResponse)

	expectedOutput := loadFile(t, "testdata/output/bad-version.txt")
	assert.Equal(t, string(expectedOutput), capturedOutput.String())

	capturedOutput.Reset()

	teardown()
}
func TestCreateCosmosHTTPJSONRequest(t *testing.T) {
	setup()

	// create a request
	request, requestBody := createExampleRequest(t)

	// check request headers, URL and body
	assert.Equal(t, "application/vnd.dcos.service.describe-response+json;charset=utf-8;version=v1", request.Header["Accept"][0])
	assert.Equal(t, "application/vnd.dcos.service.describe-request+json;charset=utf-8;version=v1", request.Header["Content-Type"][0])
	assert.Equal(t, "token=dummytoken", request.Header["Authorization"][0])
	assert.Equal(t, "https://my.dcos.url/cosmos/service/describe", request.URL.String())
	actualBody, err := ioutil.ReadAll(request.Body)
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, requestBody, actualBody)

	teardown()
}

func TestLocalCosmosUrl(t *testing.T) {
	setup()

	// create a URL where the user has manually specified a URL to Cosmos
	config.CosmosUrl = "https://my.local.cosmos/"

	describeURL := createCosmosURL("describe")
	updateURL := createCosmosURL("update")

	assert.Equal(t, "https://my.local.cosmos/service/describe", describeURL.String())
	assert.Equal(t, "https://my.local.cosmos/service/update", updateURL.String())

	config.CosmosUrl = ""

	teardown()
}

func TestCosmosUrl(t *testing.T) {
	setup()

	// create a URL where Cosmos is running on the DC/OS cluster
	describeURL := createCosmosURL("describe")
	updateURL := createCosmosURL("update")

	assert.Equal(t, "https://my.dcos.url/cosmos/service/describe", describeURL.String())
	assert.Equal(t, "https://my.dcos.url/cosmos/service/update", updateURL.String())

	teardown()
}
