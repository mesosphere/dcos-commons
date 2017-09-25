package commands

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type PlanTestSuite struct {
	suite.Suite
	server         *httptest.Server
	requestBody    []byte
	responseBody   []byte
	responseStatus int
	capturedOutput bytes.Buffer
}

func (suite *PlanTestSuite) printRecorder(format string, a ...interface{}) (n int, err error) {
	suite.capturedOutput.WriteString(fmt.Sprintf(format+"\n", a...))
	return 0, nil // this is probably sub-optimal in the general sense
}

func (suite *PlanTestSuite) loadFile(filename string) []byte {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		suite.T().Fatal(err)
	}
	return data
}

func (suite *PlanTestSuite) exampleHandler(w http.ResponseWriter, r *http.Request) {
	// write the request data to our suite's struct
	requestBody, err := ioutil.ReadAll(r.Body)
	if err != nil {
		suite.T().Fatalf("%s", err)
	}
	suite.requestBody = requestBody

	w.WriteHeader(suite.responseStatus)
	w.Write(suite.responseBody)
}

func (suite *PlanTestSuite) SetupSuite() {
	config.ModuleName = "hello-world"
	config.ServiceName = "hello-world"

	// reassign printing functions to allow us to check output
	client.PrintMessage = suite.printRecorder
	client.PrintMessageAndExit = suite.printRecorder
}

func (suite *PlanTestSuite) SetupTest() {
	// set up test server
	suite.server = httptest.NewServer(http.HandlerFunc(suite.exampleHandler))
	config.DcosURL = suite.server.URL
}

func (suite *PlanTestSuite) TearDownTest() {
	suite.capturedOutput.Reset()
	suite.server.Close()
}
func TestPlanTestSuite(t *testing.T) {
	suite.Run(t, new(PlanTestSuite))
}

func (suite *PlanTestSuite) TestGetVariablePairParsesVariable() {
	pairString := "var=value"
	pair, err := getVariablePair(pairString)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value"}) {
		suite.T().Error("Expected [\"var\", \"value\"], got ", pair)
	}
}

func (suite *PlanTestSuite) TestGetVariablePairParsesVariableWithEqualsSign() {
	pairString := "var=value=more"
	pair, err := getVariablePair(pairString)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value=more"}) {
		suite.T().Error("Expected [\"var\", \"value=more\"], got ", pair)
	}
}

func (suite *PlanTestSuite) TestGetVariablePairParsesVariableWithSpace() {
	pairString := "var=value more"
	pair, err := getVariablePair(pairString)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value more"}) {
		suite.T().Error("Expected [\"var\", \"value more\"], got ", pair)
	}
}

func (suite *PlanTestSuite) TestGetVariablePairFailsWhenEqualsSignNotPresent() {
	pairString := "var value"
	_, err := getVariablePair(pairString)

	if err == nil {
		suite.T().Error("Parsing for \"var value\" should have failed without an equals sign present")
	}
}

func (suite *PlanTestSuite) TestSingleVariableIsMarshaledToJSON() {
	parameters := []string{"var=value"}
	expectedParameters, _ := json.Marshal(map[string]string{
		"var": "value",
	})

	result, err := getPlanParameterPayload(parameters)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}
	assert.Equal(suite.T(), string(expectedParameters), result)
}

func (suite *PlanTestSuite) TestMultipleVariablesAreMarshaledToJSON() {
	parameters := []string{"var=value", "var2=value2"}
	expectedParameters, _ := json.Marshal(map[string]string{
		"var":  "value",
		"var2": "value2",
	})

	result, err := getPlanParameterPayload(parameters)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}
	assert.Equal(suite.T(), string(expectedParameters), result)
}

func (suite *PlanTestSuite) TestParseJSONResponse() {
	valid := []byte(`{"message":"Hi!"}`)
	assert.True(suite.T(), parseJSONResponse(valid))

	validJSONInvalidResponse := []byte(`{"not-a-valid-key":"Nope!"}`)
	assert.False(suite.T(), parseJSONResponse(validJSONInvalidResponse))

	invalidJSON := []byte(`{"message":"Lost a bracket!"`)
	expectedOutput := "Could not decode response: unexpected end of JSON input\n"
	assert.False(suite.T(), parseJSONResponse(invalidJSON))
	assert.Equal(suite.T(), expectedOutput, suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestGetQuery() {
	query := getQueryWithPhaseAndStep("test-phase", "test-step")
	assert.Equal(suite.T(), "test-phase", query.Get("phase"))
	assert.Equal(suite.T(), "test-step", query.Get("step"))
}

func (suite *PlanTestSuite) TestForceComplete() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/force-complete.json")
	suite.responseStatus = http.StatusOK
	forceComplete("deploy", "hello", "hello-0:[server]")
	expectedOutput := "\"deploy\" plan: step \"hello-0:[server]\" in phase \"hello\" has been forced to complete.\n"
	assert.Equal(suite.T(), expectedOutput, suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestForceRestart() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/restart.json")
	suite.responseStatus = http.StatusOK
	restart("deploy", "hello", "hello-0:[server]")
	expectedOutput := "\"deploy\" plan: step \"hello-0:[server]\" in phase \"hello\" has been restarted.\n"
	assert.Equal(suite.T(), expectedOutput, suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestPause() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/interrupt.json")
	suite.responseStatus = http.StatusOK
	config.Command = "plan pause"

	pause("deploy", "hello")
	expectedOutput := "\"deploy\" plan has been paused.\n"
	assert.Equal(suite.T(), expectedOutput, suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestPauseBadName() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/not-found.txt")
	suite.responseStatus = http.StatusNotFound
	config.Command = "plan pause"

	err := pause("bad-name", "")

	expectedOutput := "Plan, phase and/or step does not exist."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestPauseBadPhase() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/not-found.txt")
	suite.responseStatus = http.StatusNotFound
	config.Command = "plan pause"

	err := pause("deploy", "bad-phase")

	expectedOutput := "Plan, phase and/or step does not exist."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestPauseAlreadyPaused() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/already-reported.txt")
	suite.responseStatus = http.StatusAlreadyReported
	config.Command = "plan pause"

	err := pause("deploy", "hello")

	expectedOutput := "Cannot execute command. Command has already been issued or the plan has completed."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestPauseAlreadyCompleted() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/already-reported.txt")
	suite.responseStatus = http.StatusAlreadyReported
	config.Command = "plan pause"

	err := pause("deploy", "hello")

	expectedOutput := "Cannot execute command. Command has already been issued or the plan has completed."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestResume() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/continue.json")
	suite.responseStatus = http.StatusOK
	config.Command = "plan resume"

	resume("deploy", "hello")

	expectedOutput := "\"deploy\" plan has been resumed.\n"
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestResumeBadPlan() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/not-found.txt")
	suite.responseStatus = http.StatusNotFound
	config.Command = "plan resume"

	err := resume("bad-name", "")

	expectedOutput := "Plan, phase and/or step does not exist."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestResumeBadPhase() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/not-found.txt")
	suite.responseStatus = http.StatusNotFound
	config.Command = "plan resume"

	err := resume("deploy", "bad-phase")

	expectedOutput := "Plan, phase and/or step does not exist."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestResumeInProgress() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/already-reported.txt")
	suite.responseStatus = http.StatusAlreadyReported
	config.Command = "plan resume"

	err := resume("deploy", "hello")

	expectedOutput := "Cannot execute command. Command has already been issued or the plan has completed."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestResumeAlreadyCompleted() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/already-reported.txt")
	suite.responseStatus = http.StatusAlreadyReported
	config.Command = "plan resume"

	err := resume("deploy", "hello")

	expectedOutput := "Cannot execute command. Command has already been issued or the plan has completed."
	assert.Equal(suite.T(), string(expectedOutput), err.Error())
}

func (suite *PlanTestSuite) TestStatusTreeSinglePhase() {
	inputJSON := `{
  "phases" : [ {
    "id" : "e0c28f36-1a62-47b9-ae3b-a0889afe4dda",
    "name" : "Deployment",
    "steps" : [ {
      "id" : "926089db-7ad3-43bc-8565-2e0adc9bda27",
      "status" : "COMPLETE",
      "name" : "kafka-0:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[broker] [926089db-7ad3-43bc-8565-2e0adc9bda27]' has status: 'COMPLETE'."
    }, {
      "id" : "dcc46d7b-b236-4c53-ac7d-116d56059165",
      "status" : "PENDING",
      "name" : "kafka-1:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[broker] [dcc46d7b-b236-4c53-ac7d-116d56059165]' has status: 'PENDING'."
    }, {
      "id" : "994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9",
      "status" : "PENDING",
      "name" : "kafka-2:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[broker] [994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9]' has status: 'PENDING'."
    } ],
    "status" : "IN_PROGRESS"
  } ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`

	expectedOutput := `deploy (IN_PROGRESS)
└─ Deployment (IN_PROGRESS)
   ├─ kafka-0:[broker] (COMPLETE)
   ├─ kafka-1:[broker] (PENDING)
   └─ kafka-2:[broker] (PENDING)`

	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeSinglePhaseWithErrors() {
	inputJSON := `{
  "phases" : [ {
    "id" : "e0c28f36-1a62-47b9-ae3b-a0889afe4dda",
    "name" : "Deployment",
    "steps" : [ {
      "id" : "926089db-7ad3-43bc-8565-2e0adc9bda27",
      "status" : "COMPLETE",
      "name" : "kafka-0:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[broker] [926089db-7ad3-43bc-8565-2e0adc9bda27]' has status: 'COMPLETE'."
    }, {
      "id" : "dcc46d7b-b236-4c53-ac7d-116d56059165",
      "status" : "PENDING",
      "name" : "kafka-1:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[broker] [dcc46d7b-b236-4c53-ac7d-116d56059165]' has status: 'PENDING'."
    }, {
      "id" : "994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9",
      "status" : "PENDING",
      "name" : "kafka-2:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[broker] [994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9]' has status: 'PENDING'."
    } ],
    "status" : "IN_PROGRESS"
  } ],
  "errors" : [ "foo", "bar", "baz" ],
  "status" : "IN_PROGRESS"
}`

	expectedOutput := `deploy (IN_PROGRESS)
└─ Deployment (IN_PROGRESS)
   ├─ kafka-0:[broker] (COMPLETE)
   ├─ kafka-1:[broker] (PENDING)
   └─ kafka-2:[broker] (PENDING)

Errors:
- foo
- bar
- baz`

	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeMultiPhase() {
	inputJSON := `{
  "phases" : [ {
    "id" : "e0c28f36-1a62-47b9-ae3b-a0889afe4dda",
    "name" : "Deployment",
    "steps" : [ {
      "id" : "926089db-7ad3-43bc-8565-2e0adc9bda27",
      "status" : "COMPLETE",
      "name" : "kafka-0:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[broker] [926089db-7ad3-43bc-8565-2e0adc9bda27]' has status: 'COMPLETE'."
    }, {
      "id" : "dcc46d7b-b236-4c53-ac7d-116d56059165",
      "status" : "IN_PROGRESS",
      "name" : "kafka-1:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[broker] [dcc46d7b-b236-4c53-ac7d-116d56059165]' has status: 'IN_PROGRESS'."
    }, {
      "id" : "994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9",
      "status" : "PENDING",
      "name" : "kafka-2:[broker]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[broker] [994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9]' has status: 'PENDING'."
    } ],
    "status" : "IN_PROGRESS"
  }, {
    "id" : "e0c28f36-1a62-47b9-ae3b-a0889afe4dda",
    "name" : "Reindexing",
    "steps" : [ {
      "id" : "926089db-7ad3-43bc-8565-2e0adc9bda27",
      "status" : "PENDING",
      "name" : "kafka-0:[reindex]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-0:[reindex] [926089db-7ad3-43bc-8565-2e0adc9bda27]' has status: 'PENDING'."
    }, {
      "id" : "dcc46d7b-b236-4c53-ac7d-116d56059165",
      "status" : "PENDING",
      "name" : "kafka-1:[reindex]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-1:[reindex] [dcc46d7b-b236-4c53-ac7d-116d56059165]' has status: 'PENDING'."
    }, {
      "id" : "994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9",
      "status" : "PENDING",
      "name" : "kafka-2:[reindex]",
      "message" : "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'kafka-2:[reindex] [994b5ff2-ed1d-4fb2-b2a7-327e8e159ad9]' has status: 'PENDING'."
    } ],
    "status" : "PENDING"
  } ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`

	expectedOutput := `deploy (IN_PROGRESS)
├─ Deployment (IN_PROGRESS)
│  ├─ kafka-0:[broker] (COMPLETE)
│  ├─ kafka-1:[broker] (IN_PROGRESS)
│  └─ kafka-2:[broker] (PENDING)
└─ Reindexing (PENDING)
   ├─ kafka-0:[reindex] (PENDING)
   ├─ kafka-1:[reindex] (PENDING)
   └─ kafka-2:[reindex] (PENDING)`

	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeEmptyJson() {
	expectedOutput := "deploy (<UNKNOWN>)"
	result := toStatusTree("deploy", []byte("{ }"))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeNoPhases() {
	inputJSON := `{
  "phases" : [ ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
	expectedOutput := "deploy (IN_PROGRESS)"
	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeEmptyPhase() {
	inputJSON := `{
  "phases" : [ { } ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
	expectedOutput := `deploy (IN_PROGRESS)
└─ <UNKNOWN> (<UNKNOWN>)`
	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeNoSteps() {
	inputJSON := `{
  "phases" : [ {
    "id" : "e0c28f36-1a62-47b9-ae3b-a0889afe4dda",
    "name" : "Deployment",
    "steps" : [ ],
    "status" : "IN_PROGRESS"
  } ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
	expectedOutput := `deploy (IN_PROGRESS)
└─ Deployment (IN_PROGRESS)`
	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestStatusTreeEmptyStep() {
	inputJSON := `{
  "phases" : [ {
    "id" : "e0c28f36-1a62-47b9-ae3b-a0889afe4dda",
    "name" : "Deployment",
    "steps" : [ { } ],
    "status" : "IN_PROGRESS"
  } ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
	expectedOutput := `deploy (IN_PROGRESS)
└─ Deployment (IN_PROGRESS)
   └─ <UNKNOWN> (<UNKNOWN>)`
	result := toStatusTree("deploy", []byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PlanTestSuite) TestPrintStatusRaw() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/plan-status.json")
	suite.responseStatus = http.StatusOK
	printStatus("deploy", true)

	// assert CLI output matches response json
	assert.Equal(suite.T(), string(suite.responseBody)+"\n", suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestPrintStatusTree() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/plan-status.json")
	suite.responseStatus = http.StatusOK
	printStatus("deploy", false)

	// assert CLI output is what we expect
	expectedOutput := suite.loadFile("testdata/output/deploy-tree-twophase.txt")
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}

// TestPrintStatusWithError tests that the plan command handled plans that
// return 417 when one of the phases has an error that is not registered.
func (suite *PlanTestSuite) TestPrintStatusWithError() {
	suite.responseBody = []byte(`{
    "phases" : [ {
      "id" : "b35c149a-1fa2-447a-9c22-d42cc7129de4",
      "name" : "node-deploy",
      "steps" : [ {
      "id" : "1a71141b-392d-4c72-924d-82b3d4cd922a",
      "status" : "COMPLETE",
      "name" : "node-0:[server]",
      "message" : ""
      } ],
      "status" : "COMPLETE"
    } ],
    "errors" : [ "deploy error" ],
    "status" : "ERROR"
    }`)
	suite.responseStatus = http.StatusExpectationFailed

	printStatus("deploy", false)

	expectedOutput := `deploy (ERROR)
└─ node-deploy (COMPLETE)
   └─ node-0:[server] (COMPLETE)

Errors:
- deploy error
`
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}
