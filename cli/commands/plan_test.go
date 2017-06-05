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

	"github.com/alecthomas/assert"
	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/stretchr/testify/suite"
)

type PlanTestSuite struct {
	suite.Suite
	server         *httptest.Server
	requestBody    []byte
	responseBody   []byte
	capturedOutput bytes.Buffer
}

func (suite *PlanTestSuite) logRecorder(format string, a ...interface{}) {
	suite.capturedOutput.WriteString(fmt.Sprintf(format+"\n", a...))
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
		suite.T().Fatal("%s", err)
	}
	suite.requestBody = requestBody

	w.WriteHeader(http.StatusOK)
	w.Write(suite.responseBody)
}

func (suite *PlanTestSuite) SetupSuite() {
	config.ModuleName = "hello-world"
	config.ServiceName = "hello-world"

	// reassign logging and printing functions to allow us to check output
	client.LogMessage = suite.logRecorder
	client.LogMessageAndExit = suite.logRecorder
	client.PrintMessage = suite.printRecorder
}

func (suite *PlanTestSuite) SetupTest() {
	// set up test server
	suite.server = httptest.NewServer(http.HandlerFunc(suite.exampleHandler))
	config.DcosUrl = suite.server.URL
}

func (suite *PlanTestSuite) TearDownTest() {
	suite.capturedOutput.Reset()
	suite.server.Close()
}
func TestPlanTestSuite(t *testing.T) {
	suite.Run(t, new(UpdateTestSuite))
}

func (suite *PlanTestSuite) TestGetVariablePairParsesVariable() {
	pairString := "var=value"
	pair, err := GetVariablePair(pairString)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value"}) {
		suite.T().Error("Expected [\"var\", \"value\"], got ", pair)
	}
}

func (suite *PlanTestSuite) TestGetVariablePairParsesVariableWithEqualsSign() {
	pairString := "var=value=more"
	pair, err := GetVariablePair(pairString)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value=more"}) {
		suite.T().Error("Expected [\"var\", \"value=more\"], got ", pair)
	}
}

func (suite *PlanTestSuite) TestGetVariablePairParsesVariableWithSpace() {
	pairString := "var=value more"
	pair, err := GetVariablePair(pairString)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value more"}) {
		suite.T().Error("Expected [\"var\", \"value more\"], got ", pair)
	}
}

func (suite *PlanTestSuite) TestGetVariablePairFailsWhenEqualsSignNotPresent() {
	pairString := "var value"
	_, err := GetVariablePair(pairString)

	if err == nil {
		suite.T().Error("Parsing for \"var value\" should have failed without an equals sign present")
	}
}

func (suite *PlanTestSuite) TestSingleVariableIsMarshaledToJSON() {
	parameters := []string{"var=value"}
	expectedParameters, _ := json.Marshal(map[string]string{
		"var": "value",
	})

	result, err := GetPlanParameterPayload(parameters)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if string(expectedParameters) != result {
		suite.T().Error("Expected ", string(expectedParameters), ", got ", result)
	}
}

func (suite *PlanTestSuite) TestMultipleVariablesAreMarshaledToJSON() {
	parameters := []string{"var=value", "var2=value2"}
	expectedParameters, _ := json.Marshal(map[string]string{
		"var":  "value",
		"var2": "value2",
	})

	result, err := GetPlanParameterPayload(parameters)

	if err != nil {
		suite.T().Error("Got error: ", err)
	}

	if string(expectedParameters) != result {
		suite.T().Error("Expected ", string(expectedParameters), ", got ", result)
	}
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
   └─ kafka-2:[broker] (PENDING)
`

	result := toStatusTree("deploy", []byte(inputJSON))
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
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
- baz
`

	result := toStatusTree("deploy", []byte(inputJSON))
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
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
   └─ kafka-2:[reindex] (PENDING)
`

	result := toStatusTree("deploy", []byte(inputJSON))
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
}

func (suite *PlanTestSuite) TestStatusTreeEmptyJson() {
	expectedOutput := "deploy (<UNKNOWN>)"
	result := toStatusTree("deploy", []byte("{ }"))
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
}

func (suite *PlanTestSuite) TestStatusTreeNoPhases() {
	inputJSON := `{
  "phases" : [ ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
	expectedOutput := `deploy (IN_PROGRESS)`
	result := toStatusTree("deploy", []byte(inputJSON))
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
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
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
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
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
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
	if expectedOutput != result {
		suite.T().Error("Expected ", expectedOutput, ", got ", result)
	}
}

func (suite *PlanTestSuite) TestPrintStatusRaw() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/plan-status.json")
	printStatus("deploy", true)

	// assert CLI output matches response json
	assert.Equal(suite.T(), string(suite.responseBody)+"\n\n", suite.capturedOutput.String())
}

func (suite *PlanTestSuite) TestPrintStatusTree() {
	suite.responseBody = suite.loadFile("testdata/responses/scheduler/plan-status.json")
	printStatus("deploy", false)

	// assert CLI output is what we expect
	expectedOutput := `deploy (IN_PROGRESS)
├─ Deployment (IN_PROGRESS)
│  ├─ kafka-0:[broker] (COMPLETE)
│  ├─ kafka-1:[broker] (IN_PROGRESS)
│  └─ kafka-2:[broker] (PENDING)
└─ Reindexing (PENDING)
   ├─ kafka-0:[reindex] (PENDING)
   ├─ kafka-1:[reindex] (PENDING)
   └─ kafka-2:[reindex] (PENDING)
`
	assert.Equal(suite.T(), string(expectedOutput), suite.capturedOutput.String())
}
