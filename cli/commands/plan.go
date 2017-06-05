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

func GetPlanName(cmd *PlanHandler) string {
	plan := "deploy"
	if len(cmd.PlanName) > 0 {
		plan = cmd.PlanName
	}
	return plan
}

func (cmd *PlanHandler) RunList(c *kingpin.ParseContext) error {
	response := client.HTTPServiceGet("v1/plans")
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunStatus(c *kingpin.ParseContext) error {
	planName := GetPlanName(cmd)
	response := client.HTTPServiceGet(fmt.Sprintf("v1/plans/%s", planName))
	if cmd.RawJSON {
		client.PrintJSON(response)
	} else {
		client.PrintMessage(toStatusTree(planName, client.GetResponseBytes(response)))
	}
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

func (cmd *PlanHandler) RunStop(c *kingpin.ParseContext) error {
	response := client.HTTPServicePost(fmt.Sprintf("v1/plans/%s/stop", cmd.PlanName))
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunResume(c *kingpin.ParseContext) error {
	query := url.Values{}
	if len(cmd.Phase) > 0 {
		query.Set("phase", cmd.Phase)
	}
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/continue", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunInterrupt(c *kingpin.ParseContext) error {
	query := url.Values{}
	if len(cmd.Phase) > 0 {
		query.Set("phase", cmd.Phase)
	}
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/interrupt", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunRestart(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("phase", cmd.Phase)
	query.Set("step", cmd.Step)
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/restart", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunForceComplete(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("phase", cmd.Phase)
	query.Set("step", cmd.Step)
	response := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/forceComplete", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func HandlePlanSection(app *kingpin.Application) {
	// plan <active, continue, force, interrupt, restart, status/show>
	cmd := &PlanHandler{}
	plan := app.Command("plan", "Query service plans")

	plan.Command("list", "Show all plans for this service").Action(cmd.RunList)

	status := plan.Command("status", "Display the deploy plan or the plan with the provided name").Alias("show").Action(cmd.RunStatus)
	status.Arg("plan", "Name of the plan to show").StringVar(&cmd.PlanName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.RawJSON)

	start := plan.Command("start", "Start the plan with the provided name, with optional envvars to supply to task").Action(cmd.RunStart)
	start.Arg("plan", "Name of the plan to start").Required().StringVar(&cmd.PlanName)
	start.Flag("params", "Envvar definition in VAR=value form; can be repeated for multiple variables").Short('p').StringsVar(&cmd.Parameters)

	force := plan.Command("force-complete", "Force complete the plan with the provided name").Alias("force").Action(cmd.RunForceComplete)
	force.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.PlanName)
	force.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&cmd.Phase)
	force.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&cmd.Step)

	pause := plan.Command("pause", "Pause the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Alias("interrupt").Action(cmd.RunInterrupt)
	pause.Arg("plan", "Name of the plan to interrupt").StringVar(&cmd.PlanName)
	pause.Arg("phase", "Name or UUID of a specific phase to interrupt").StringVar(&cmd.Phase)

	restart := plan.Command("restart", "Restart the plan with the provided name, or the specific step in the provided phase (each by name or UUID)").Action(cmd.RunRestart)
	restart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.PlanName)
	restart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&cmd.Phase) // TODO optional
	restart.Arg("step", "Name or UUID of step to be restarted").StringVar(&cmd.Step)

	resume := plan.Command("resume", "Continue the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Alias("continue").Action(cmd.RunResume)
	resume.Arg("plan", "Name of the plan to continue").StringVar(&cmd.PlanName)
	resume.Arg("phase", "Name or UUID of a specific phase to continue").StringVar(&cmd.Phase)

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
