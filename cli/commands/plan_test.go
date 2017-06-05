package commands

import (
	"encoding/json"
	"reflect"
	"testing"
)

func TestGetVariablePairParsesVariable(t *testing.T) {
	pairString := "var=value"
	pair, err := GetVariablePair(pairString)

	if err != nil {
		t.Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value"}) {
		t.Error("Expected [\"var\", \"value\"], got ", pair)
	}
}

func TestGetVariablePairParsesVariableWithEqualsSign(t *testing.T) {
	pairString := "var=value=more"
	pair, err := GetVariablePair(pairString)

	if err != nil {
		t.Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value=more"}) {
		t.Error("Expected [\"var\", \"value=more\"], got ", pair)
	}
}

func TestGetVariablePairParsesVariableWithSpace(t *testing.T) {
	pairString := "var=value more"
	pair, err := GetVariablePair(pairString)

	if err != nil {
		t.Error("Got error: ", err)
	}

	if !reflect.DeepEqual(pair, []string{"var", "value more"}) {
		t.Error("Expected [\"var\", \"value more\"], got ", pair)
	}
}

func TestGetVariablePairFailsWhenEqualsSignNotPresent(t *testing.T) {
	pairString := "var value"
	_, err := GetVariablePair(pairString)

	if err == nil {
		t.Error("Parsing for \"var value\" should have failed without an equals sign present")
	}
}

func TestSingleVariableIsMarshaledToJSON(t *testing.T) {
	parameters := []string{"var=value"}
	expectedParameters, _ := json.Marshal(map[string]string{
		"var": "value",
	})

	result, err := GetPlanParameterPayload(parameters)

	if err != nil {
		t.Error("Got error: ", err)
	}

	if string(expectedParameters) != result {
		t.Error("Expected ", string(expectedParameters), ", got ", result)
	}
}

func TestMultipleVariablesAreMarshaledToJSON(t *testing.T) {
	parameters := []string{"var=value", "var2=value2"}
	expectedParameters, _ := json.Marshal(map[string]string{
		"var":  "value",
		"var2": "value2",
	})

	result, err := GetPlanParameterPayload(parameters)

	if err != nil {
		t.Error("Got error: ", err)
	}

	if string(expectedParameters) != result {
		t.Error("Expected ", string(expectedParameters), ", got ", result)
	}
}

func TestStatusTreeSinglePhase(t *testing.T) {
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
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeSinglePhaseWithErrors(t *testing.T) {
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
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeMultiPhase(t *testing.T) {
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
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeEmptyJson(t *testing.T) {
	expectedOutput := `deploy (<UNKNOWN>)
`
	result := toStatusTree("deploy", []byte("{ }"))
	if expectedOutput != result {
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeNoPhases(t *testing.T) {
	inputJSON := `{
  "phases" : [ ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
<<<<<<< HEAD
	expectedOutput := `deploy (IN_PROGRESS)
`
	result := toStatusTree("deploy", []byte(inputJson))
=======
	expectedOutput := `deploy (IN_PROGRESS)`
	result := toStatusTree("deploy", []byte(inputJSON))
>>>>>>> Tidy up variable names to make warnings go away and reorder plan subcommands.
	if expectedOutput != result {
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeEmptyPhase(t *testing.T) {
	inputJSON := `{
  "phases" : [ { } ],
  "errors" : [ ],
  "status" : "IN_PROGRESS"
}`
	expectedOutput := `deploy (IN_PROGRESS)
└─ <UNKNOWN> (<UNKNOWN>)`
	result := toStatusTree("deploy", []byte(inputJSON))
	if expectedOutput != result {
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeNoSteps(t *testing.T) {
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
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}

func TestStatusTreeEmptyStep(t *testing.T) {
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
		t.Error("Expected ", expectedOutput, ", got ", result)
	}
}
