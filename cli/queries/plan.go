package queries

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"strings"

	"net/http"

	"github.com/mesosphere/dcos-commons/cli/client"
)

const UNKNOWN_VALUE = "<UNKNOWN>"

var errPlanStatus417 = errors.New("plan endpoint returned HTTP status code 417")

type plansResponse struct {
	Message string `json:"message"`
}

func parsePlansJSONResponse(jsonBytes []byte) bool {
	var response plansResponse
	err := json.Unmarshal(jsonBytes, &response)
	if err != nil {
		return false
	}
	if len(response.Message) > 0 {
		// we're just checking that we were able to set the message
		// since json.Unmarshal will still work as long as the original
		// []byte object is a well formed JSON string.
		return true
	}
	return false
}

func checkPlansResponse(response *http.Response, body []byte) error {
	switch {
	case response.StatusCode == http.StatusNotFound:
		if string(body) == "Element not found" {
			// The scheduler itself is returning the 404 (otherwise we fall through to the default Adminrouter case)
			return errors.New("Plan, phase, and/or step does not exist.")
		}
	case response.StatusCode == http.StatusAlreadyReported:
		return errors.New("Cannot execute command. Command has already been issued or the plan has completed.")
	case response.StatusCode == http.StatusExpectationFailed:
		return errPlanStatus417
	}
	return nil
}

func getQueryWithPhaseAndStep(phase, step string) url.Values {
	query := url.Values{}
	if len(phase) > 0 {
		query.Set("phase", phase)
	}
	if len(step) > 0 {
		query.Set("step", step)
	}
	return query
}

type Plan struct {
	PrefixCb func() string
}

func NewPlan() *Plan {
	return &Plan{
		PrefixCb: func() string { return "v1/" },
	}
}

func (q *Plan) ForceComplete(planName, phase, step string) error {
	query := getQueryWithPhaseAndStep(phase, step)
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("%splans/%s/forceComplete", q.PrefixCb(), planName), query.Encode())
	if err != nil {
		return err
	}
	if parsePlansJSONResponse(responseBytes) {
		client.PrintMessage("\"%s\" plan: step \"%s\" in phase \"%s\" has been forced to complete.", planName, step, phase)
	} else {
		client.PrintMessage("\"%s\" plan: step \"%s\" in phase \"%s\" could not be forced to complete.", planName, step, phase)
	}
	return nil
}

func (q *Plan) ForceRestart(planName, phase, step string) error {
	query := getQueryWithPhaseAndStep(phase, step)
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("%splans/%s/restart", q.PrefixCb(), planName), query.Encode())
	if err != nil {
		return err
	}
	if parsePlansJSONResponse(responseBytes) {
		if step == "" && phase == "" {
			client.PrintMessage("\"%s\" plan has been restarted.", planName)
		} else if step == "" {
			client.PrintMessage("\"%s\" plan: phase \"%s\" has been restarted.", planName, phase)
		} else {
			client.PrintMessage("\"%s\" plan: step \"%s\" in phase \"%s\" has been restarted.", planName, step, phase)
		}
	} else {
		if step == "" && phase == "" {
			client.PrintMessage("\"%s\" plan could not be restarted.", planName)
		} else if step == "" {
			client.PrintMessage("\"%s\" plan: phase \"%s\" could not be restarted.", planName, phase)
		} else {
			client.PrintMessage("\"%s\" plan: step \"%s\" in phase \"%s\" could not be restarted.", planName, step, phase)
		}
	}
	return nil
}

func (q *Plan) List() error {
	responseBytes, err := client.HTTPServiceGet(q.PrefixCb() + "plans")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}

func (q *Plan) Pause(planName, phase string) error {
	query := getQueryWithPhaseAndStep(phase, "")
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("%splans/%s/interrupt", q.PrefixCb(), planName), query.Encode())
	if err != nil {
		return err
	}
	if parsePlansJSONResponse(responseBytes) {
		if phase == "" {
			client.PrintMessage("\"%s\" plan has been paused.", planName)
		} else {
			client.PrintMessage("\"%s\" plan: phase \"%s\" has been paused.", planName, phase)
		}
	} else {
		if phase == "" {
			client.PrintMessage("\"%s\" plan could not be paused.", planName)
		} else {
			client.PrintMessage("\"%s\" plan: phase \"%s\" could not be paused.", planName, phase)
		}
	}
	return nil
}

func (q *Plan) Resume(planName, phase string) error {
	query := getQueryWithPhaseAndStep(phase, "")
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("%splans/%s/continue", q.PrefixCb(), planName), query.Encode())
	if err != nil {
		return err
	}
	if parsePlansJSONResponse(responseBytes) {
		if phase == "" {
			client.PrintMessage("\"%s\" plan has been resumed.", planName)
		} else {
			client.PrintMessage("\"%s\" plan: phase \"%s\" has been resumed.", planName, phase)
		}
	} else {
		if phase == "" {
			client.PrintMessage("\"%s\" plan could not be resumed.", planName)
		} else {
			client.PrintMessage("\"%s\" plan: phase \"%s\" could not be resumed.", planName, phase)
		}
	}
	return nil
}

func (q *Plan) Start(planName string, parameters []string) error {
	payload := []byte("{}")
	if len(parameters) > 0 {
		parameterPayload, err := getPlanParameterPayload(parameters)
		if err != nil {
			return err
		}
		payload = parameterPayload
	}
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostJSON(fmt.Sprintf("%splans/%s/start", q.PrefixCb(), planName), payload)
	if err != nil {
		return err
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}

func getPlanParameterPayload(parameters []string) ([]byte, error) {
	envPairs := make(map[string]string)
	for _, pairString := range parameters {
		elements := strings.Split(pairString, "=")
		if len(elements) < 2 {
			return nil, fmt.Errorf(
				"Must have one variable name and one variable value per definition")
		}
		pair := []string{elements[0], strings.Join(elements[1:], "=")}
		envPairs[pair[0]] = pair[1]
	}

	jsonVal, err := json.Marshal(envPairs)
	if err != nil {
		return nil, err
	}

	return jsonVal, nil
}

func (q *Plan) Status(planName string, rawJSON bool) error {
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServiceGet(fmt.Sprintf("%splans/%s", q.PrefixCb(), planName))

	if err != nil && err != errPlanStatus417 {
		return err
	}
	if rawJSON {
		client.PrintJSONBytes(responseBytes)
	} else {
		tree, err := toPlanStatusTree(planName, responseBytes)
		if err != nil {
			return err
		}
		client.PrintMessage(tree)
	}
	return nil
}

func (q *Plan) Stop(planName string) error {
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePost(fmt.Sprintf("%splans/%s/stop", q.PrefixCb(), planName))
	if err != nil {
		return err
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}

func toPlanStatusTree(planName string, planJSONBytes []byte) (string, error) {
	planJSON, err := client.UnmarshalJSON(planJSONBytes)
	if err != nil {
		return "", err
	}
	var buf bytes.Buffer

	planStatus, ok := planJSON["status"]
	if !ok {
		planStatus = UNKNOWN_VALUE
	}
	planStrategy, ok := planJSON["strategy"]
	if !ok {
		planStrategy = UNKNOWN_VALUE
	}
	buf.WriteString(fmt.Sprintf("%s (%s strategy) (%s)\n", planName, planStrategy, planStatus))

	rawPhases, ok := planJSON["phases"].([]interface{})
	if ok {
		for i, rawPhase := range rawPhases {
			appendPhase(&buf, rawPhase, i == len(rawPhases)-1)
		}
	}

	errors, ok := planJSON["errors"].([]interface{})
	if ok && len(errors) > 0 {
		buf.WriteString("\nErrors:\n")
		for _, error := range errors {
			buf.WriteString(fmt.Sprintf("- %s\n", error))
		}
	}

	return strings.TrimRight(buf.String(), "\n"), nil
}

func appendPhase(buf *bytes.Buffer, rawPhase interface{}, lastPhase bool) {
	var phasePrefix string
	var stepPrefix string
	if lastPhase {
		phasePrefix = "└─ "
		stepPrefix = "   "
	} else {
		phasePrefix = "├─ "
		stepPrefix = "│  "
	}

	phase, ok := rawPhase.(map[string]interface{})
	if !ok {
		return
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", phasePrefix, phaseString(phase)))

	rawSteps, ok := phase["steps"].([]interface{})
	if !ok {
		return
	}
	for i, rawStep := range rawSteps {
		appendStep(buf, rawStep, stepPrefix, i == len(rawSteps)-1)
	}
}

func appendStep(buf *bytes.Buffer, rawStep interface{}, prefix string, lastStep bool) {
	step, ok := rawStep.(map[string]interface{})
	if !ok {
		return
	}

	if lastStep {
		prefix += "└─ "
	} else {
		prefix += "├─ "
	}
	buf.WriteString(fmt.Sprintf("%s%s\n", prefix, stepString(step)))
}

func phaseString(phase map[string]interface{}) string {
	phaseName, ok := phase["name"]
	if !ok {
		phaseName = UNKNOWN_VALUE
	}
	phaseStrategy, ok := phase["strategy"]
	if !ok {
		phaseStrategy = UNKNOWN_VALUE
	}
	phaseStatus, ok := phase["status"]
	if !ok {
		phaseStatus = UNKNOWN_VALUE
	}
	return fmt.Sprintf("%s (%s strategy) (%s)", phaseName, phaseStrategy, phaseStatus)
}

func stepString(step map[string]interface{}) string {
	stepName, ok := step["name"]
	if !ok {
		stepName = UNKNOWN_VALUE
	}
	stepStatus, ok := step["status"]
	if !ok {
		stepStatus = UNKNOWN_VALUE
	}
	return fmt.Sprintf("%s (%s)", stepName, stepStatus)
}
