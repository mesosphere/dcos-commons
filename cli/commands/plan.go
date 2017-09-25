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
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

var errPlanStatus417 = errors.New("plan endpoint returned HTTP status code 417")

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
	// there is no (and should not be) a case where the plan name is requested here where it is not needed.
	// this invariant should be guarded by CLI validators, but since other commands, such as `update` route
	// through the `plan` command, counting on such a guard is error prone, so underlying guard applied here.
	client.PrintMessageAndExit("Must specify a plan name, e.g. 'deploy'")
	return ""
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

func forceComplete(planName, phase, step string) {
	query := getQueryWithPhaseAndStep(phase, step)
	client.SetCustomResponseCheck(checkPlansResponse)
	responseBytes, err := client.HTTPServicePostQuery(fmt.Sprintf("v1/plans/%s/forceComplete", planName), query.Encode())
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if parseJSONResponse(responseBytes) {
		client.PrintMessage("\"%s\" plan: step \"%s\" in phase \"%s\" has been forced to complete.", planName, step, phase)
	} else {
		client.PrintMessage("\"%s\" plan: step \"%s\" in phase \"%s\" could not be forced to complete.", planName, step, phase)
	}
}

func (cmd *planHandler) handleForceComplete(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
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
}

func (cmd *planHandler) handleForceRestart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	restart(cmd.getPlanName(), cmd.Phase, cmd.Step)
	return nil
}

func (cmd *planHandler) handleList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
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
		client.PrintMessage("\"%s\" plan has been paused.", planName)
	} else {
		client.PrintMessage("\"%s\" plan could not be paused.", planName)
	}
	return nil
}

func (cmd *planHandler) handlePause(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
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
		client.PrintMessage("\"%s\" plan has been resumed.", planName)
	} else {
		client.PrintMessage("\"%s\" plan could not be resumed.", planName)
	}
	return nil
}

func (cmd *planHandler) handleResume(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	err := resume(cmd.getPlanName(), cmd.Phase)
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	return nil
}

func (cmd *planHandler) handleStart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
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

	if err != nil && err != errPlanStatus417 {
		client.PrintMessageAndExit(err.Error())
	}
	if rawJSON {
		client.PrintJSONBytes(responseBytes)
	} else {
		client.PrintMessage(toStatusTree(planName, responseBytes))
	}
}

func (cmd *planHandler) handleStatus(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	printStatus(cmd.getPlanName(), cmd.RawJSON)
	return nil
}

func (cmd *planHandler) handleStop(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
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

	plan.Command("list", "Show all plans for this service").Action(cmd.handleList)

	status := plan.Command("status", "Display the status of the plan with the provided plan name").Alias("show").Action(cmd.handleStatus)
	status.Arg("plan", "Name of the plan to show").Required().StringVar(&cmd.PlanName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.RawJSON)

	start := plan.Command("start", "Start the plan with the provided name and any optional plan arguments").Action(cmd.handleStart)
	start.Arg("plan", "Name of the plan to start").Required().StringVar(&cmd.PlanName)
	start.Flag("params", "Envvar definition in VAR=value form; can be repeated for multiple variables").Short('p').StringsVar(&cmd.Parameters)

	stop := plan.Command("stop", "Stop the running plan with the provided name").Action(cmd.handleStop)
	stop.Arg("plan", "Name of the plan to stop").Required().StringVar(&cmd.PlanName)

	pause := plan.Command("pause", "Pause the plan, or a specific phase in that plan with the provided phase name (or UUID)").Alias("interrupt").Action(cmd.handlePause)
	pause.Arg("plan", "Name of the plan to pause").Required().StringVar(&cmd.PlanName)
	pause.Arg("phase", "Name or UUID of a specific phase to pause").StringVar(&cmd.Phase)

	resume := plan.Command("resume", "Resume the plan, or a specific phase in that plan with the provided phase name (or UUID)").Alias("continue").Action(cmd.handleResume)
	resume.Arg("plan", "Name of the plan to resume").Required().StringVar(&cmd.PlanName)
	resume.Arg("phase", "Name or UUID of a specific phase to continue").StringVar(&cmd.Phase)

	forceRestart := plan.Command("force-restart", "Restart the plan with the provided name, or a specific phase in the plan with the provided name, or a specific step in a phase of the plan with the provided step name.").Alias("restart").Action(cmd.handleForceRestart)
	forceRestart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.PlanName)
	forceRestart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&cmd.Phase) // TODO optional
	forceRestart.Arg("step", "Name or UUID of step to be restarted").StringVar(&cmd.Step)

	forceComplete := plan.Command("force-complete", "Force complete a specific step in the provided phase. Example uses include the following: Abort a sidecar operation due to observed failure or known required manual preparation that was not performed").Alias("force").Action(cmd.handleForceComplete)
	forceComplete.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.PlanName)
	forceComplete.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&cmd.Phase)
	forceComplete.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&cmd.Step)
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
