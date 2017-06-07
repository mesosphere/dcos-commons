package commands

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

// Plan section

type PlanHandler struct {
	PlanName   string
	Parameters []string
	Phase      string
	Step       string
	RawJSON    bool
}

func GetVariablePair(pairString string) ([]string, error) {
	elements := strings.Split(pairString, "=")
	if len(elements) < 2 {
		return nil, fmt.Errorf(
			"Must have one variable name and one variable value per definition")
	}

	return []string{elements[0], strings.Join(elements[1:], "=")}, nil
}

func GetPlanParameterPayload(parameters []string) (string, error) {
	envPairs := make(map[string]string)
	for _, pairString := range parameters {
		pair, err := GetVariablePair(pairString)
		if err != nil {
			return "", err
		}
		envPairs[pair[0]] = pair[1]
	}

	jsonVal, err := json.Marshal(envPairs)
	if err != nil {
		return "", err
	}

	return string(jsonVal), nil
}

func (cmd *PlanHandler) getPlanName() string {
	plan := "deploy"
	if len(cmd.PlanName) > 0 {
		plan = cmd.PlanName
	}
	return plan
}

type PlansResponse struct {
	Message string `json:"message"`
}

func parseJSONResponse(jsonBytes []byte) bool {
	var response PlansResponse
	err := json.Unmarshal(jsonBytes, &response)
	if err != nil {
		client.LogMessage("Could not decode response: %s", err)
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

func forceComplete(planName, phase, step string) {
	query := getQueryWithPhaseAndStep(phase, step)
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/forceComplete", planName), query.Encode())
	if parseJSONResponse(client.GetResponseBytes(response)) {
		client.LogMessage("Step %s in phase %s in plan %s has been forced to complete.", step, phase, planName)
	} else {
		client.LogMessage("Step %s in phase %s in plan %s could not be forced to complete.", step, phase, planName)
	}
}

func (cmd *PlanHandler) RunForceComplete(c *kingpin.ParseContext) error {
	forceComplete(cmd.getPlanName(), cmd.Phase, cmd.Step)
	return nil
}

func restart(planName, phase, step string) {
	query := getQueryWithPhaseAndStep(phase, step)
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/restart", planName), query.Encode())
	if parseJSONResponse(client.GetResponseBytes(response)) {
		// TODO: the user doesn't always have to specify this down to plan level so we should output different messages
		client.LogMessage("Step %s in phase %s in plan %s has been restarted.", step, phase, planName)
	} else {
		client.LogMessage("Step %s in phase %s in plan %s could not be restarted.", step, phase, planName)
	}
}

func (cmd *PlanHandler) RunForceRestart(c *kingpin.ParseContext) error {
	restart(cmd.getPlanName(), cmd.Phase, cmd.Step)
	return nil
}

func (cmd *PlanHandler) RunList(c *kingpin.ParseContext) error {
	response := client.HTTPServiceGet("v1/plans")
	client.PrintJSON(response)
	return nil
}

func pause(planName, phase string) {
	query := getQueryWithPhaseAndStep(phase, "")
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/interrupt", planName), query.Encode())
	if parseJSONResponse(client.GetResponseBytes(response)) {
		client.LogMessage("Plan %s has been paused.", planName)
	} else {
		client.LogMessage("Plan %s could not be paused.", planName)
	}
}

func (cmd *PlanHandler) RunPause(c *kingpin.ParseContext) error {
	pause(cmd.getPlanName(), cmd.Phase)
	return nil
}

func resume(planName, phase string) {
	query := getQueryWithPhaseAndStep(phase, "")
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/continue", planName), query.Encode())
	if parseJSONResponse(client.GetResponseBytes(response)) {
		client.LogMessage("Plan %s has been resumed.", planName)
	} else {
		client.LogMessage("Plan %s could not be resumed.", planName)
	}
}

func (cmd *PlanHandler) RunResume(c *kingpin.ParseContext) error {
	resume(cmd.getPlanName(), cmd.Phase)
	return nil
}

func (cmd *PlanHandler) RunStart(c *kingpin.ParseContext) error {
	payload := "{}"
	if len(cmd.Parameters) > 0 {
		parameterPayload, err := GetPlanParameterPayload(cmd.Parameters)
		if err != nil {
			return err
		}
		payload = parameterPayload
	}
	response := client.HTTPServicePostData(fmt.Sprintf("v1/plans/%s/start", cmd.PlanName), payload, "application/json")
	client.PrintJSON(response)
	return nil
}

func printStatus(planName string, rawJSON bool) {
	response := client.HTTPServiceGet(fmt.Sprintf("v1/plans/%s", planName))
	if rawJSON {
		client.PrintJSON(response)
	} else {
		client.LogMessage(toStatusTree(planName, client.GetResponseBytes(response)))
	}
}

func (cmd *PlanHandler) RunStatus(c *kingpin.ParseContext) error {
	printStatus(cmd.getPlanName(), cmd.RawJSON)
	return nil
}

func (cmd *PlanHandler) RunStop(c *kingpin.ParseContext) error {
	response := client.HTTPServicePost(fmt.Sprintf("v1/plans/%s/stop", cmd.PlanName))
	client.PrintJSON(response)
	return nil
}

func HandlePlanSection(app *kingpin.Application) {
	// plan <active, continue, force, interrupt, restart, status/show>
	cmd := &PlanHandler{}
	plan := app.Command("plan", "Query service plans")

	forceComplete := plan.Command("force-complete", "Force complete a specific step in the provided phase").Alias("force").Action(cmd.RunForceComplete)
	forceComplete.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.PlanName)
	forceComplete.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&cmd.Phase)
	forceComplete.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&cmd.Step)

	forceRestart := plan.Command("force-restart", "Restart a deploy plan, or specific step in the provided phase").Alias("restart").Action(cmd.RunForceRestart)
	forceRestart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.PlanName)
	forceRestart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&cmd.Phase) // TODO optional
	forceRestart.Arg("step", "Name or UUID of step to be restarted").StringVar(&cmd.Step)

	plan.Command("list", "Show all plans for this service").Action(cmd.RunList)

	pause := plan.Command("pause", "Pause the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Alias("interrupt").Action(cmd.RunPause)
	pause.Arg("plan", "Name of the plan to pause").StringVar(&cmd.PlanName)
	pause.Arg("phase", "Name or UUID of a specific phase to pause").StringVar(&cmd.Phase)

	resume := plan.Command("resume", "Resume the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Alias("continue").Action(cmd.RunResume)
	resume.Arg("plan", "Name of the plan to resume").StringVar(&cmd.PlanName)
	resume.Arg("phase", "Name or UUID of a specific phase to continue").StringVar(&cmd.Phase)

	start := plan.Command("start", "Start the plan with the provided name, with optional envvars to supply to task").Action(cmd.RunStart)
	start.Arg("plan", "Name of the plan to start").Required().StringVar(&cmd.PlanName)
	start.Flag("params", "Envvar definition in VAR=value form; can be repeated for multiple variables").Short('p').StringsVar(&cmd.Parameters)

	status := plan.Command("status", "Display the deploy plan or the plan with the provided name").Alias("show").Action(cmd.RunStatus)
	status.Arg("plan", "Name of the plan to show").StringVar(&cmd.PlanName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.RawJSON)

	stop := plan.Command("stop", "Stop the plan with the provided name").Action(cmd.RunStop)
	stop.Arg("plan", "Name of the plan to stop").Required().StringVar(&cmd.PlanName)
}

func toStatusTree(planName string, planJSONBytes []byte) string {
	optionsJSON, err := client.UnmarshalJSON(planJSONBytes)
	if err != nil {
		client.LogMessageAndExit(fmt.Sprintf("Failed to parse JSON in plan response: %s", err))
	}
	var buf bytes.Buffer

	planStatus, ok := optionsJSON["status"]
	if !ok {
		planStatus = "<UNKNOWN>"
	}
	buf.WriteString(fmt.Sprintf("%s (%s)\n", planName, planStatus))

	phases, ok := optionsJSON["phases"].([]interface{})
	if ok {
		for i, rawPhase := range phases {
			appendPhase(&buf, rawPhase, i == len(phases) - 1)
		}
	}

	errors, ok := optionsJSON["errors"].([]interface{})
	if ok && len(errors) > 0 {
		buf.WriteString("\nErrors:\n")
		for _, error := range errors {
			buf.WriteString(fmt.Sprintf("- %s\n", error))
		}
	}

	// Include extra newline at end:
	return buf.String()
}

func appendPhase(buf *bytes.Buffer, rawPhase interface{}, lastPhase bool) {
	var phasePrefix string
	if lastPhase {
		phasePrefix = "└─ "
	} else {
		phasePrefix = "├─ "
	}

	phase, ok := rawPhase.(map[string]interface{})
	if !ok {
		return
	}

	buf.WriteString(elementString(phasePrefix, phase))

	steps, ok := phase["steps"].([]interface{})
	if !ok {
		return
	}
	for i, rawStep := range steps {
		appendStep(buf, rawStep, lastPhase, i == len(steps) - 1)
	}
}

func appendStep(buf *bytes.Buffer, rawStep interface{}, lastPhase bool, lastStep bool) {
	var stepPrefix string
	if lastPhase {
		if lastStep {
			stepPrefix = "   └─ "
		} else {
			stepPrefix = "   ├─ "
		}
	} else {
		if lastStep {
			stepPrefix = "│  └─ "
		} else {
			stepPrefix = "│  ├─ "
		}
	}

	step, ok := rawStep.(map[string]interface{})
	if !ok {
		return
	}

	buf.WriteString(elementString(stepPrefix, step))
}

func elementString(prefix string, element map[string]interface{}) string {
	elementName, ok := element["name"]
	if !ok {
		elementName = "<UNKNOWN>"
	}
	elementStatus, ok := element["status"]
	if !ok {
		elementStatus = "<UNKNOWN>"
	}
	return fmt.Sprintf("%s%s (%s)\n", prefix, elementName, elementStatus)
}
