package cli

import (
	"errors"
	"fmt"
	"gopkg.in/alecthomas/kingpin.v2"
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

func HandleCommonArgs(
	app *kingpin.Application,
	defaultServiceName string,
	shortDescription string,
	connectionTypes []string) {
	HandleCommonFlags(app, defaultServiceName, shortDescription)
	HandleCommonSections(app, connectionTypes)
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

// All sections

func HandleCommonSections(app *kingpin.Application, connectionTypes []string) {
	HandleConfigSection(app)
	HandleConnectionSection(app, connectionTypes)
	HandlePlanSection(app)
	HandleStateSection(app)
	HandleHealthSection(app)
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

func HandleConnectionSection(app *kingpin.Application, connectionTypes []string) {
	// connection [type]
	cmd := &ConnectionHandler{}
	connection := app.Command("connection", fmt.Sprintf("View connection information (custom types: %s)", strings.Join(connectionTypes, ", "))).Action(cmd.RunConnection)
	if len(connectionTypes) != 0 {
		connection.Arg("type", fmt.Sprintf("Custom type of the connection data to display (%s)", strings.Join(connectionTypes, ", "))).StringVar(&cmd.TypeName)
	}
}

// Plan section

type PlanHandler struct {
}

func (cmd *PlanHandler) RunActive(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/plan/status"))
	return nil
}
func (cmd *PlanHandler) RunContinue(c *kingpin.ParseContext) error {
	PrintJSON(HTTPPost("v1/plan/continue"))
	return nil
}
func (cmd *PlanHandler) RunForce(c *kingpin.ParseContext) error {
	PrintJSON(HTTPPost("v1/plan/forceComplete"))
	return nil
}
func (cmd *PlanHandler) RunInterrupt(c *kingpin.ParseContext) error {
	PrintJSON(HTTPPost("v1/plan/interrupt"))
	return nil
}
func (cmd *PlanHandler) RunRestart(c *kingpin.ParseContext) error {
	PrintJSON(HTTPPost("v1/plan/restart"))
	return nil
}
func (cmd *PlanHandler) RunShow(c *kingpin.ParseContext) error {
	// custom behavior: ignore 503 error
	response := HTTPQuery(CreateHTTPRequest("GET", "v1/plan"))
	if response.StatusCode != 503 {
		CheckHTTPResponse(response)
	}
	PrintJSON(response)
	return nil
}

func HandlePlanSection(app *kingpin.Application) {
	// plan <active, continue, force, interrupt, restart, show>
	cmd := &PlanHandler{}
	plan := app.Command("plan", "Query service plans")

	plan.Command("active", "Display the active operation chain, if any").Action(cmd.RunActive)
	plan.Command("continue", "Continue a currently Waiting operation").Action(cmd.RunContinue)
	plan.Command("force", "Force the current operation to complete").Action(cmd.RunForce)
	plan.Command("interrupt", "Interrupt the current InProgress operation").Action(cmd.RunInterrupt)
	plan.Command("restart", "Restart the current operation").Action(cmd.RunRestart)
	plan.Command("show", "Display the full plan").Action(cmd.RunShow)
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
	PrintJSON(HTTPGet(fmt.Sprintf("v1/state/tasks/status/%s", cmd.TaskName)))
	return nil
}
func (cmd *StateHandler) RunTask(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/state/tasks/info/%s", cmd.TaskName)))
	return nil
}
func (cmd *StateHandler) RunTasks(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/state/tasks"))
	return nil
}

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

// Health section

type HealthHandler struct {
}

func (cmd *HealthHandler) RunHealthCheck(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("admin/healthcheck"))
	return nil
}

func HandleHealthSection(app *kingpin.Application) {
	// health
	cmd := &HealthHandler{}
	app.Command("health", fmt.Sprintf("View healthcheck information)")).Action(cmd.RunHealthCheck)
}
