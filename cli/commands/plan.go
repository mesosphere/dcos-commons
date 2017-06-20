package commands

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"strings"

	"net/http"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
	"gopkg.in/alecthomas/kingpin.v2"
)

type planHandler struct {
	PlanName   string
	Parameters []string
	Phase      string
	Step       string
	RawJSON    bool
}

func getVariablePair(pairString string) ([]string, error) {
	elements := strings.Split(pairString, "=")
	if len(elements) < 2 {
		return nil, fmt.Errorf(
			"Must have one variable name and one variable value per definition")
	}

	return []string{elements[0], strings.Join(elements[1:], "=")}, nil
}

func getPlanParameterPayload(parameters []string) (string, error) {
	envPairs := make(map[string]string)
	for _, pairString := range parameters {
		pair, err := getVariablePair(pairString)
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

func (cmd *planHandler) getPlanName() string {
	if len(cmd.PlanName) > 0 {
		return cmd.PlanName
	}
	return "deploy"
}

type plansResponse struct {
	Message string `json:"message"`
}

func parseJSONResponse(jsonBytes []byte) bool {
	var response plansResponse
	err := json.Unmarshal(jsonBytes, &response)
	if err != nil {
		client.PrintMessage("Could not decode response: %s", err)
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
			return errors.New("Plan, phase and/or step does not exist.")
		}
	case response.StatusCode == http.StatusAlreadyReported:
		return errors.New("Cannot execute command. Command has already been issued or the plan has completed.")
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

func forceComplete(planName, phase, step string) {
	query := getQueryWithPhaseAndStep(phase, step)
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/forceComplete", planName), query.Encode())
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if parseJSONResponse(responseBytes) {
		client.PrintMessage("Step %s in phase %s in plan %s has been forced to complete.", step, phase, planName)
	} else {
		client.PrintMessage("Step %s in phase %s in plan %s could not be forced to complete.", step, phase, planName)
	}
}

func (cmd *planHandler) handleForceComplete(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	forceComplete(cmd.getPlanName(), cmd.Phase, cmd.Step)
	return nil
}

func restart(planName, phase, step string) {
	query := getQueryWithPhaseAndStep(phase, step)
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/restart", planName), query.Encode())
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if parseJSONResponse(responseBytes) {
		// TODO: the user doesn't always have to specify this down to plan level so we should output different messages
		if step == "" && phase == "" {
			client.PrintMessage("Plan %s has been restarted.", planName)
		} else {
			client.PrintMessage("Step %s in phase %s in plan %s has been restarted.", step, phase, planName)
		}
	} else {
		if step == "" && phase == "" {
			client.PrintMessage("Plan %s could not be restarted.", planName)
		} else {
			client.PrintMessage("Step %s in phase %s in plan %s could not be restarted.", step, phase, planName)
		}
	}
}

func (cmd *planHandler) handleForceRestart(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	restart(cmd.getPlanName(), cmd.Phase, cmd.Step)
	return nil
}

func (cmd *planHandler) handleList(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	responseBytes, err := client.HTTPServiceGet("v1/plans")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}

func pause(planName, phase string) error {
	query := getQueryWithPhaseAndStep(phase, "")
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/interrupt", planName), query.Encode())
	if err != nil {
		return err
	}
	if parseJSONResponse(responseBytes) {
		client.PrintMessage("Plan %s has been paused.", planName)
	} else {
		client.PrintMessage("Plan %s could not be paused.", planName)
	}
	return nil
}

func (cmd *planHandler) handlePause(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	err := pause(cmd.getPlanName(), cmd.Phase)
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	return nil
}

func resume(planName, phase string) error {
	query := getQueryWithPhaseAndStep(phase, "")
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/continue", planName), query.Encode())
	if err != nil {
		return err
	}
	if parseJSONResponse(responseBytes) {
		client.PrintMessage("Plan %s has been resumed.", planName)
	} else {
		client.PrintMessage("Plan %s could not be resumed.", planName)
	}
	return nil
}

func (cmd *planHandler) handleResume(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	err := resume(cmd.getPlanName(), cmd.Phase)
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	return nil
}

func (cmd *planHandler) handleStart(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	payload := "{}"
	if len(cmd.Parameters) > 0 {
		parameterPayload, err := getPlanParameterPayload(cmd.Parameters)
		if err != nil {
			return err
		}
		payload = parameterPayload
	}
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostData(fmt.Sprintf("v1/plans/%s/start", cmd.PlanName), payload, "application/json")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}

func printStatus(planName string, rawJSON bool) {
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServiceGet(fmt.Sprintf("v1/plans/%s", planName))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if rawJSON {
		client.PrintJSONBytes(responseBytes)
	} else {
		client.PrintMessage(toStatusTree(planName, responseBytes))
	}
}

func (cmd *planHandler) handleStatus(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	printStatus(cmd.getPlanName(), cmd.RawJSON)
	return nil
}

func (cmd *planHandler) handleStop(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePost(fmt.Sprintf("v1/plans/%s/stop", cmd.PlanName))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}

// HandlePlanSection adds plan subcommands to the passed in kingpin.Application.
func HandlePlanSection(app *kingpin.Application) {
	// plan <active, continue, force, interrupt, restart, status/show>
	cmd := &planHandler{}
	plan := app.Command("plan", "Query service plans")

	forceComplete := plan.Command("force-complete", "Force complete a specific step in the provided phase").Alias("force").Action(cmd.handleForceComplete)
	forceComplete.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.PlanName)
	forceComplete.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&cmd.Phase)
	forceComplete.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&cmd.Step)

	forceRestart := plan.Command("force-restart", "Restart a deploy plan, or specific step in the provided phase").Alias("restart").Action(cmd.handleForceRestart)
	forceRestart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.PlanName)
	forceRestart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&cmd.Phase) // TODO optional
	forceRestart.Arg("step", "Name or UUID of step to be restarted").StringVar(&cmd.Step)

	plan.Command("list", "Show all plans for this service").Action(cmd.handleList)

	pause := plan.Command("pause", "Pause the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Alias("interrupt").Action(cmd.handlePause)
	pause.Arg("plan", "Name of the plan to pause").StringVar(&cmd.PlanName)
	pause.Arg("phase", "Name or UUID of a specific phase to pause").StringVar(&cmd.Phase)

	resume := plan.Command("resume", "Resume the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Alias("continue").Action(cmd.handleResume)
	resume.Arg("plan", "Name of the plan to resume").StringVar(&cmd.PlanName)
	resume.Arg("phase", "Name or UUID of a specific phase to continue").StringVar(&cmd.Phase)

	start := plan.Command("start", "Start the plan with the provided name, with optional envvars to supply to task").Action(cmd.handleStart)
	start.Arg("plan", "Name of the plan to start").Required().StringVar(&cmd.PlanName)
	start.Flag("params", "Envvar definition in VAR=value form; can be repeated for multiple variables").Short('p').StringsVar(&cmd.Parameters)

	status := plan.Command("status", "Display the deploy plan or the plan with the provided name").Alias("show").Action(cmd.handleStatus)
	status.Arg("plan", "Name of the plan to show").StringVar(&cmd.PlanName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.RawJSON)

	stop := plan.Command("stop", "Stop the plan with the provided name").Action(cmd.handleStop)
	stop.Arg("plan", "Name of the plan to stop").Required().StringVar(&cmd.PlanName)
}

func toStatusTree(planName string, planJSONBytes []byte) string {
	optionsJSON, err := client.UnmarshalJSON(planJSONBytes)
	if err != nil {
		client.PrintMessageAndExit(fmt.Sprintf("Failed to parse JSON in plan response: %s", err))
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
			appendPhase(&buf, rawPhase, i == len(phases)-1)
		}
	}

	errors, ok := optionsJSON["errors"].([]interface{})
	if ok && len(errors) > 0 {
		buf.WriteString("\nErrors:\n")
		for _, error := range errors {
			buf.WriteString(fmt.Sprintf("- %s\n", error))
		}
	}

	// Trim extra newline from end:
	buf.Truncate(buf.Len() - 1)

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
		appendStep(buf, rawStep, lastPhase, i == len(steps)-1)
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
