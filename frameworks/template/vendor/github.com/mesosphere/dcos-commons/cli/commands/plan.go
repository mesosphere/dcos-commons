package commands

import (
	"encoding/json"
	"errors"
	"fmt"
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
	"net/url"
	"strings"
)

// Plan section

type PlanHandler struct {
    PlanName   string
    Parameters []string
    Phase      string
    Step       string
}

func GetVariablePair(pairString string) ([]string, error) {
	elements := strings.Split(pairString, "=")
	if len(elements) < 2 {
		return nil, errors.New(fmt.Sprintf(
			"Must have one variable name and one variable value per definition"))
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
	response := client.HTTPGet("/v1/plans")
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunShow(c *kingpin.ParseContext) error {
	response := client.HTTPQuery(client.CreateHTTPRequest("GET", fmt.Sprintf("v1/plans/%s", GetPlanName(cmd))))
	client.CheckHTTPResponse(response)
	client.PrintJSON(response)
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
	response := client.HTTPPostData(fmt.Sprintf("v1/plans/%s/start", cmd.PlanName), payload, "application/json")
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunStop(c *kingpin.ParseContext) error {
	response := client.HTTPPost(fmt.Sprintf("v1/plans/%s/stop", cmd.PlanName))
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunContinue(c *kingpin.ParseContext) error {
	query := url.Values{}
	if len(cmd.Phase) > 0 {
		query.Set("phase", cmd.Phase)
	}
	response := client.HTTPPostQuery(fmt.Sprintf("v1/plans/%s/continue", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunInterrupt(c *kingpin.ParseContext) error {
	query := url.Values{}
	if len(cmd.Phase) > 0 {
		query.Set("phase", cmd.Phase)
	}
	response := client.HTTPPostQuery(fmt.Sprintf("v1/plans/%s/interrupt", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunRestart(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("phase", cmd.Phase)
	query.Set("step", cmd.Step)
	response := client.HTTPPostQuery(fmt.Sprintf("v1/plans/%s/restart", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunForce(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("phase", cmd.Phase)
	query.Set("step", cmd.Step)
	response := client.HTTPPostQuery(fmt.Sprintf("v1/plans/%s/forceComplete", GetPlanName(cmd)), query.Encode())
	client.PrintJSON(response)
	return nil
}

func HandlePlanSection(app *kingpin.Application) {
	// plan <active, continue, force, interrupt, restart, show>
	cmd := &PlanHandler{}
	plan := app.Command("plan", "Query service plans")

	plan.Command("list", "Show all plans for this service").Action(cmd.RunList)

	show := plan.Command("show", "Display the deploy plan or the plan with the provided name").Action(cmd.RunShow)
	show.Arg("plan", "Name of the plan to show").StringVar(&cmd.PlanName)

    start := plan.Command("start", "Start the plan with the provided name, with optional envvars to supply to task").Action(cmd.RunStart)
    start.Arg("plan", "Name of the plan to start").Required().StringVar(&cmd.PlanName)
    start.Flag("params", "Envvar definition in VAR=value form; can be repeated for multiple variables").Short('p').StringsVar(&cmd.Parameters)

	stop := plan.Command("stop", "Stop the plan with the provided name").Action(cmd.RunStop)
	stop.Arg("plan", "Name of the plan to stop").Required().StringVar(&cmd.PlanName)

	continueCmd := plan.Command("continue", "Continue the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Action(cmd.RunContinue)
	continueCmd.Arg("plan", "Name of the plan to continue").StringVar(&cmd.PlanName)
	continueCmd.Arg("phase", "Name or UUID of a specific phase to continue").StringVar(&cmd.Phase)

	interrupt := plan.Command("interrupt", "Interrupt the deploy plan, or the plan with the provided name, or a specific phase in that plan with the provided name or UUID").Action(cmd.RunInterrupt)
	interrupt.Arg("plan", "Name of the plan to interrupt").StringVar(&cmd.PlanName)
	interrupt.Arg("phase", "Name or UUID of a specific phase to interrupt").StringVar(&cmd.Phase)

	restart := plan.Command("restart", "Restart the plan with the provided name, or the specific step in the provided phase (each by name or UUID)").Action(cmd.RunRestart)
	restart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.PlanName)
	restart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&cmd.Phase) // TODO optional
	restart.Arg("step", "Name or UUID of step to be restarted").StringVar(&cmd.Step)

	force := plan.Command("force", "Force complete the plan with the provided name").Action(cmd.RunForce)
	force.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.PlanName)
	force.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&cmd.Phase)
	force.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&cmd.Step)
}
