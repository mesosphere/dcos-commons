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

const unknownValue = "<UNKNOWN>"

// A separate error for this case, allowing the Status call to ignore it.
var errPlanStatus417 = errors.New("Plan endpoint returned HTTP Expectation Failed response")

type plansResponse struct {
	Message string `json:"message"`
}

func validatePlansJSONResponse(jsonBytes []byte) bool {
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
			return errors.New("Plan, phase, and/or step does not exist")
		}
	case response.StatusCode == http.StatusAlreadyReported:
		return errors.New("Cannot execute command. Command has already been issued or the plan has completed")
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
	if validatePlansJSONResponse(responseBytes) {
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
	if validatePlansJSONResponse(responseBytes) {
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
	if validatePlansJSONResponse(responseBytes) {
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
	if validatePlansJSONResponse(responseBytes) {
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

type stepResponse struct {
	Name   string `json:"name"`
	Status string `json:"status"`
}
type phaseResponse struct {
	Name     string         `json:"name"`
	Status   string         `json:"status"`
	Strategy string         `json:"strategy"`
	Steps    []stepResponse `json:"steps"`
}

type planResponse struct {
	Status   string          `json:"status"`
	Strategy string          `json:"strategy"`
	Phases   []phaseResponse `json:"phases"`
	Errors   []interface{}   `json:"errors"`
}

func toPlanStatusTree(planName string, planJSONBytes []byte) (string, error) {
	plan := planResponse{}
	err := json.Unmarshal(planJSONBytes, &plan)
	if err != nil {
		return "", err
	}
	var buf bytes.Buffer

	if len(plan.Status) == 0 {
		plan.Status = unknownValue
	}
	if len(plan.Strategy) == 0 {
		plan.Strategy = unknownValue
	}
	buf.WriteString(fmt.Sprintf("%s (%s strategy) (%s)\n", planName, plan.Strategy, plan.Status))
	for i, phase := range plan.Phases {
		appendPhase(&buf, &phase, i == len(plan.Phases)-1)
	}
	if len(plan.Errors) > 0 {
		buf.WriteString("\nErrors:\n")
		for _, error := range plan.Errors {
			buf.WriteString(fmt.Sprintf("- %s\n", error))
		}
	}

	return strings.TrimRight(buf.String(), "\n"), nil
}

func appendPhase(buf *bytes.Buffer, phase *phaseResponse, lastPhase bool) {
	var phasePrefix string
	var stepPrefix string
	if lastPhase {
		phasePrefix = "└─ "
		stepPrefix = "   "
	} else {
		phasePrefix = "├─ "
		stepPrefix = "│  "
	}

	if len(phase.Name) == 0 {
		phase.Name = unknownValue
	}
	if len(phase.Status) == 0 {
		phase.Status = unknownValue
	}
	if len(phase.Strategy) == 0 {
		phase.Strategy = unknownValue
	}
	buf.WriteString(fmt.Sprintf("%s%s (%s strategy) (%s)\n", phasePrefix, phase.Name, phase.Strategy, phase.Status))
	for i, step := range phase.Steps {
		appendStep(buf, &step, stepPrefix, i == len(phase.Steps)-1)
	}
}

func appendStep(buf *bytes.Buffer, step *stepResponse, prefix string, lastStep bool) {
	if lastStep {
		prefix += "└─ "
	} else {
		prefix += "├─ "
	}

	if len(step.Name) == 0 {
		step.Name = unknownValue
	}
	if len(step.Status) == 0 {
		step.Status = unknownValue
	}
	buf.WriteString(fmt.Sprintf("%s%s (%s)\n", prefix, step.Name, step.Status))
}
