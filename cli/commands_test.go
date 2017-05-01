package cli

import (
	"testing"
	"reflect"
	"encoding/json"
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
		"var": "value",
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
