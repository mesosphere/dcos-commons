package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"gopkg.in/alecthomas/kingpin.v2"
	"net/http"
	"net/url"
	"os"
	"strings"
)

var (
	Verbose bool
)

func GetModuleName() (string, error) {
	if len(os.Args) < 2 {
		return "", errors.New(fmt.Sprintf(
			"Must have at least one argument for the CLI module name: %s <modname>", os.Args[0]))
	}
	return os.Args[1], nil
}

func GetArguments() []string {
	// Exercise validation of argument count:
	if len(os.Args) < 2 {
		return make([]string, 0)
	}
	return os.Args[2:]
}

func GetPlanParameterPayload(parameters string) (string, error) {
	envPairs := make(map[string]string)
	for _, pair := range strings.Split(parameters, ",") {
		elements := strings.Split(pair, "=")
		if len(elements) != 2 {
			return "", errors.New(fmt.Sprintf(
				"Must have one variable name and one variable value per definition"))
		}
		envPairs[elements[0]] = elements[1]
	}

	jsonVal, err := json.Marshal(envPairs)
	if err != nil {
		return "", err
	}

	return string(jsonVal), nil
}

func NewApp(version string, author string, longDescription string) (*kingpin.Application, error) {
	modName, err := GetModuleName()
	if err != nil {
		return nil, err
	}

	app := kingpin.New(modName, longDescription)
	app.Version(version)
	app.Author(author)
	return app, nil
}

// Add all of the below arguments and commands

// TODO remove this deprecated function on or after Feb 1 2017.
// No longer invoked in any repo's 'master' branch as of Dec 22 2016.
func HandleCommonArgs(
	app *kingpin.Application,
	defaultServiceName string,
	shortDescription string,
	connectionTypes []string) {
	HandleCommonFlags(app, defaultServiceName, shortDescription)
	HandleConfigSection(app)
	HandleConnectionSection(app, connectionTypes)
	//HandleEndpointsSection(app) omitted since callers likely don't have this
	HandlePlanSection(app)
	HandleStateSection(app)
}

// Standard Arguments

func HandleCommonFlags(app *kingpin.Application, defaultServiceName string, shortDescription string) {
	app.HelpFlag.Short('h') // in addition to default '--help'
	app.Flag("verbose", "Enable extra logging of requests/responses").Short('v').BoolVar(&Verbose)

	// This fulfills an interface that's expected by the main DC/OS CLI:
	// Prints a description of the module.
	app.Flag("info", "Show short description.").PreAction(func(*kingpin.ParseContext) error {
		fmt.Fprintf(os.Stdout, "%s\n", shortDescription)
		os.Exit(0)
		return nil
	}).Bool()

	app.Flag("force-insecure", "Allow unverified TLS certificates when querying service").BoolVar(&tlsForceInsecure)

	// Overrides of data that we fetch from DC/OS CLI:

	// Support using "DCOS_AUTH_TOKEN" or "AUTH_TOKEN" when available
	app.Flag("custom-auth-token", "Custom auth token to use when querying service").Envar("DCOS_AUTH_TOKEN").PlaceHolder("DCOS_AUTH_TOKEN").StringVar(&dcosAuthToken)
	// Support using "DCOS_URI" or "DCOS_URL" when available
	app.Flag("custom-dcos-url", "Custom cluster URL to use when querying service").Envar("DCOS_URI").Envar("DCOS_URL").PlaceHolder("DCOS_URI/DCOS_URL").StringVar(&dcosUrl)
	// Support using "DCOS_CA_PATH" or "DCOS_CERT_PATH" when available
	app.Flag("custom-cert-path", "Custom TLS CA certificate file to use when querying service").Envar("DCOS_CA_PATH").Envar("DCOS_CERT_PATH").PlaceHolder("DCOS_CA_PATH/DCOS_CERT_PATH").StringVar(&tlsCACertPath)

	// Default to --name <name> : use provided framework name (default to <modulename>.service_name, if available)
	overrideServiceName := OptionalCLIConfigValue(fmt.Sprintf("%s.service_name", os.Args[1]))
	if len(overrideServiceName) != 0 {
		defaultServiceName = overrideServiceName
	}
	app.Flag("name", "Name of the service instance to query").Default(defaultServiceName).StringVar(&ServiceName)
}

// Config section

type ConfigHandler struct {
	ShowId string
}

func (cmd *ConfigHandler) RunList(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/configurations"))
	return nil
}
func (cmd *ConfigHandler) RunShow(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/configurations/%s", cmd.ShowId)))
	return nil
}
func (cmd *ConfigHandler) RunTarget(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/configurations/target"))
	return nil
}
func (cmd *ConfigHandler) RunTargetId(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/configurations/targetId"))
	return nil
}

func HandleConfigSection(app *kingpin.Application) {
	// config <list, show, target, target_id>
	cmd := &ConfigHandler{}
	config := app.Command("config", "View persisted configurations")

	config.Command("list", "List IDs of all available configurations").Action(cmd.RunList)

	show := config.Command("show", "Display a specified configuration").Action(cmd.RunShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.ShowId)

	config.Command("target", "Display the target configuration").Action(cmd.RunTarget)

	config.Command("target_id", "List ID of the target configuration").Action(cmd.RunTargetId)
}

// Connection section

type ConnectionHandler struct {
	TypeName string
}

func (cmd *ConnectionHandler) RunConnection(c *kingpin.ParseContext) error {
	if len(cmd.TypeName) == 0 {
		// Root endpoint: Always produce JSON
		PrintJSON(HTTPGet("v1/connection"))
	} else {
		// Any custom type endpoints: May be any format, so just print the raw text
		PrintText(HTTPGet(fmt.Sprintf("v1/connection/%s", cmd.TypeName)))
	}
	return nil
}

// TODO remove this command once callers have migrated to HandleEndpointsSection().
func HandleConnectionSection(app *kingpin.Application, connectionTypes []string) {
	// connection [type]
	cmd := &ConnectionHandler{}
	connection := app.Command("connection", fmt.Sprintf("View connection information (custom types: %s)", strings.Join(connectionTypes, ", "))).Action(cmd.RunConnection)
	if len(connectionTypes) != 0 {
		connection.Arg("type", fmt.Sprintf("Custom type of the connection data to display (%s)", strings.Join(connectionTypes, ", "))).StringVar(&cmd.TypeName)
	}
}

// Endpoints section

type EndpointsHandler struct {
	Native bool
	Name   string
}

func (cmd *EndpointsHandler) RunEndpoints(c *kingpin.ParseContext) error {
	path := "v1/endpoints"
	if len(cmd.Name) != 0 {
		path += "/" + cmd.Name
	}
	var response *http.Response
	if cmd.Native {
		query := url.Values{}
		query.Set("format", "native")
		response = HTTPGetQuery(path, query.Encode())
	} else {
		response = HTTPGet(path)
	}
	if len(cmd.Name) == 0 {
		// Root endpoint: Always produce JSON
		PrintJSON(response)
	} else {
		// Any specific endpoints: May be any format, so just print the raw text
		PrintText(response)
	}
	return nil
}

func HandleEndpointsSection(app *kingpin.Application) {
	// connection [type]
	cmd := &EndpointsHandler{}
	endpoints := app.Command("endpoints", "View client endpoints").Action(cmd.RunEndpoints)
	endpoints.Flag("native", "Show native endpoints instead of Mesos-DNS endpoints").BoolVar(&cmd.Native)
	endpoints.Arg("name", "Name of specific endpoint to be returned").StringVar(&cmd.Name)
}

// Plan section

type PlanHandler struct {
	PlanName   string
	Parameters string
	PhaseId    string
	StepId     string
}

func GetPlanName(cmd *PlanHandler) string {
	plan := "deploy"
	if len(cmd.PlanName) > 0 {
		plan = cmd.PlanName
	}
	return plan
}

func (cmd *PlanHandler) RunList(c *kingpin.ParseContext) error {
	response := HTTPGet("/v1/plans")
	PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunShow(c *kingpin.ParseContext) error {
	response := HTTPQuery(CreateHTTPRequest("GET", fmt.Sprintf("v1/plans/%s", GetPlanName(cmd))))

	// custom behavior: ignore 503 error
	if response.StatusCode != 503 {
		CheckHTTPResponse(response)
	}
	PrintJSON(response)
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
	response := HTTPPostData(fmt.Sprintf("v1/plans/%s/start", cmd.PlanName), payload, "application/json")
	PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunStop(c *kingpin.ParseContext) error {
	response := HTTPPost(fmt.Sprintf("v1/plans/%s/stop", cmd.PlanName))
	PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunContinue(c *kingpin.ParseContext) error {
	response := HTTPPost(fmt.Sprintf("v1/plans/%s/continue", GetPlanName(cmd)))
	PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunInterrupt(c *kingpin.ParseContext) error {
	response := HTTPPost(fmt.Sprintf("v1/plans/%s/interrupt", GetPlanName(cmd)))
	PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunRestart(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("phase", cmd.PhaseId)
	query.Set("step", cmd.StepId)
	response := HTTPPostQuery(fmt.Sprintf("v1/plans/%s/restart", GetPlanName(cmd)), query.Encode())
	PrintJSON(response)
	return nil
}

func (cmd *PlanHandler) RunForce(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("phase", cmd.PhaseId)
	query.Set("step", cmd.StepId)
	response := HTTPPostQuery(fmt.Sprintf("v1/plans/%s/forceComplete", GetPlanName(cmd)), query.Encode())
	PrintJSON(response)
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
	start.Arg("params", "Comma-separated list of VAR=value pairs").StringVar(&cmd.Parameters)

	stop := plan.Command("stop", "Stop the plan with the provided name").Action(cmd.RunStop)
	stop.Arg("plan", "Name of the plan to stop").Required().StringVar(&cmd.PlanName)

	continueCmd := plan.Command("continue", "Continue the deploy plan or the plan with the provided name").Action(cmd.RunContinue)
	continueCmd.Arg("plan", "Name of the plan to continue").StringVar(&cmd.PlanName)

	interrupt := plan.Command("interrupt", "Interrupt the deploy plan or the plan with the provided name").Action(cmd.RunInterrupt)
	interrupt.Arg("plan", "Name of the plan to interrupt").StringVar(&cmd.PlanName)

	restart := plan.Command("restart", "Restart the plan with the provided name").Action(cmd.RunRestart)
	restart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.PlanName)
	restart.Arg("phase", "UUID of the Phase containing the provided Step").Required().StringVar(&cmd.PhaseId)
	restart.Arg("step", "UUID of Step to be restarted").Required().StringVar(&cmd.StepId)

	force := plan.Command("force", "Force complete the plan with the provided name").Action(cmd.RunForce)
	force.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.PlanName)
	force.Arg("phase", "UUID of the Phase containing the provided Step").Required().StringVar(&cmd.PhaseId)
	force.Arg("step", "UUID of Step to be restarted").Required().StringVar(&cmd.StepId)
}

// Pods section

type PodsHandler struct {
	PodName string
}

func (cmd *PodsHandler) RunList(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/pods"))
	return nil
}
func (cmd *PodsHandler) RunStatus(c *kingpin.ParseContext) error {
	if len(cmd.PodName) == 0 {
		PrintJSON(HTTPGet("v1/pods/status"))
	} else {
		PrintJSON(HTTPGet(fmt.Sprintf("v1/pods/%s/status", cmd.PodName)))
	}
	return nil
}
func (cmd *PodsHandler) RunInfo(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/pods/%s/info", cmd.PodName)))
	return nil
}
func (cmd *PodsHandler) RunRestart(c *kingpin.ParseContext) error {
	PrintText(HTTPPost(fmt.Sprintf("v1/pods/%s/restart", cmd.PodName)))
	return nil
}
func (cmd *PodsHandler) RunReplace(c *kingpin.ParseContext) error {
	PrintText(HTTPPost(fmt.Sprintf("v1/pods/%s/replace", cmd.PodName)))
	return nil
}

func HandlePodsSection(app *kingpin.Application) {
	// pods [status [name], info <name>, restart <name>, replace <name>]
	cmd := &PodsHandler{}
	pods := app.Command("pods", "View Pod/Task state")

	pods.Command("list", "Display the list of known pod instances").Action(cmd.RunList)

	status := pods.Command("status", "Display the status for tasks in one pod or all pods").Action(cmd.RunStatus)
	status.Arg("pod", "Name of a specific pod instance to display").StringVar(&cmd.PodName)

	info := pods.Command("info", "Display the full state information for tasks in a pod").Action(cmd.RunInfo)
	info.Arg("pod", "Name of the pod instance to display").Required().StringVar(&cmd.PodName)

	restart := pods.Command("restart", "Restarts a given pod without moving it to a new agent").Action(cmd.RunRestart)
	restart.Arg("pod", "Name of the pod instance to restart").Required().StringVar(&cmd.PodName)

	replace := pods.Command("replace", "Destroys a given pod and moves it to a new agent").Action(cmd.RunReplace)
	replace.Arg("pod", "Name of the pod instance to replace").Required().StringVar(&cmd.PodName)
}

// State section

type StateHandler struct {
	TaskName string
}

func (cmd *StateHandler) RunFrameworkId(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/state/frameworkId"))
	return nil
}
func (cmd *StateHandler) RunStatus(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/tasks/status/%s", cmd.TaskName)))
	return nil
}
func (cmd *StateHandler) RunTask(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/tasks/info/%s", cmd.TaskName)))
	return nil
}
func (cmd *StateHandler) RunTasks(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/tasks"))
	return nil
}

// TODO remove this command once callers have migrated to HandlePodsSection().
func HandleStateSection(app *kingpin.Application) {
	// state <framework_id, status, task, tasks>
	cmd := &StateHandler{}
	state := app.Command("state", "View persisted state")

	state.Command("framework_id", "Display the mesos framework ID").Action(cmd.RunFrameworkId)

	status := state.Command("status", "Display the TaskStatus for a task name").Action(cmd.RunStatus)
	status.Arg("name", "Name of the task to display").Required().StringVar(&cmd.TaskName)

	task := state.Command("task", "Display the TaskInfo for a task name").Action(cmd.RunTask)
	task.Arg("name", "Name of the task to display").Required().StringVar(&cmd.TaskName)

	state.Command("tasks", "List names of all persisted tasks").Action(cmd.RunTasks)
}
