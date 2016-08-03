package cli

import (
	"errors"
	"fmt"
	"gopkg.in/alecthomas/kingpin.v2"
	"os"
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

func HandleCommonArgs(app *kingpin.Application, defaultServiceName string, shortDescription string) {
	HandleCommonFlags(app, defaultServiceName, shortDescription)
	HandleCommonSections(app)
}

// Standard Arguments

func HandleCommonFlags(app *kingpin.Application, defaultServiceName string, shortDescription string) {
	// -h (in addition to --help): standard help
	app.HelpFlag.Short('h')
	// -v/--verbose: show extra info about requests being made
	app.Flag("verbose", "Enable extra logging of requests/responses").Short('v').BoolVar(&Verbose)

	// --custom_auth_token <token> : use provided auth token instead of dcos CLI token
	app.Flag("custom-auth-token", "Custom auth token to use when querying service").OverrideDefaultFromEnvar("DCOS_AUTH_TOKEN").OverrideDefaultFromEnvar("AUTH_TOKEN").StringVar(&dcosAuthToken)
	// --custom-dcos-url <url> : use provided cluster url instead of dcos CLI url
	app.Flag("custom-dcos-url", "Custom cluster URL to use when querying service").OverrideDefaultFromEnvar("DCOS_URI").OverrideDefaultFromEnvar("DCOS_URL").StringVar(&dcosUrl)

	// --info : command to print description (fulfills interface that's expected by DC/OS CLI)
	app.Flag("info", "Show short description.").PreAction(func(*kingpin.ParseContext) error {
		fmt.Fprintf(os.Stdout, "%s\n", shortDescription)
		os.Exit(0)
		return nil
	}).Bool()

	// --name <name> : use provided framework name (default to <modulename>.service_name, if available)
	overrideServiceName, err := RunDCOSCLICommand(
		"config", "show", fmt.Sprintf("%s.service_name", os.Args[1]))
	if err == nil {
		defaultServiceName = overrideServiceName
	}
	app.Flag("name", "Name of the service instance to query").Default(defaultServiceName).StringVar(&serviceName)
}

// All sections

func HandleCommonSections(app *kingpin.Application) {
	HandleConfigSection(app)
	HandleConnectionSection(app)
	HandlePlanSection(app)
	HandleStateSection(app)
}

// Config section

type ConfigHandler struct {
	showId string
}

func (cmd *ConfigHandler) runList(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/configurations"))
	return nil
}
func (cmd *ConfigHandler) runShow(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/configurations/%s", cmd.showId)))
	return nil
}
func (cmd *ConfigHandler) runTarget(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/configurations/target"))
	return nil
}
func (cmd *ConfigHandler) runTargetId(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/configurations/targetId"))
	return nil
}

func HandleConfigSection(app *kingpin.Application) {
	// config <list, show, target, target_id>
	cmd := &ConfigHandler{}
	config := app.Command("config", "View persisted configurations")

	config.Command("list", "List IDs of all available configurations").Action(cmd.runList)

	show := config.Command("show", "Display a specified configuration").Action(cmd.runShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.showId)

	config.Command("target", "Display the target configuration").Action(cmd.runTarget)

	config.Command("target_id", "List ID of the target configuration").Action(cmd.runTargetId)
}

// Connection section

type ConnectionHandler struct {
	typeName string
}

func (cmd *ConnectionHandler) runConnection(c *kingpin.ParseContext) error {
	if len(cmd.typeName) == 0 {
		// Root endpoint: Always produce JSON
		PrintJSON(HTTPGet("v1/connection"))
	} else {
		// Any custom type endpoints: May be any format, so just print the raw text
		PrintText(HTTPGet(fmt.Sprintf("v1/connection/%s", cmd.typeName)))
	}
	return nil
}

func HandleConnectionSection(app *kingpin.Application) {
	// connection [type]
	cmd := &ConnectionHandler{}
	connection := app.Command("connection", "View connection information").Action(cmd.runConnection)
	connection.Arg("type", "Type of connection information to retrieve").StringVar(&cmd.typeName)
}

// Plan section

type PlanHandler struct {
}

func (cmd *PlanHandler) runActive(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/plan/status"))
	return nil
}
func (cmd *PlanHandler) runContinue(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/plan/continue"))
	return nil
}
func (cmd *PlanHandler) runForce(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/plan/forceComplete"))
	return nil
}
func (cmd *PlanHandler) runInterrupt(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/plan/interrupt"))
	return nil
}
func (cmd *PlanHandler) runRestart(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/plan/restart"))
	return nil
}
func (cmd *PlanHandler) runShow(c *kingpin.ParseContext) error {
	// custom behavior: ignore 503 error
	response := HTTPQuery(CreateHTTPRequest("GET", "v1/plan"), false)
	if response.StatusCode != 503 &&
		(response.StatusCode < 200 || response.StatusCode >= 300) {
		return errors.New(fmt.Sprintf("Got HTTP status: %s", response.Status))
	}
	PrintJSON(response)
	return nil
}

func HandlePlanSection(app *kingpin.Application) {
	// plan <active, continue, force, interrupt, restart, show>
	cmd := &PlanHandler{}
	plan := app.Command("plan", "Query service plans")

	plan.Command("active", "Display the active operation chain, if any").Action(cmd.runActive)
	plan.Command("continue", "Continue a currently Waiting operation").Action(cmd.runContinue)
	plan.Command("force", "Force the current operation to complete").Action(cmd.runForce)
	plan.Command("interrupt", "Interrupt the current InProgress operation").Action(cmd.runInterrupt)
	plan.Command("restart", "Restart the current operation").Action(cmd.runRestart)
	plan.Command("show", "Display the full plan").Action(cmd.runShow)
}

// State section

type StateHandler struct {
	TaskName string
}

func (cmd *StateHandler) runFrameworkId(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/state/frameworkId"))
	return nil
}
func (cmd *StateHandler) runStatus(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/state/tasks/status/%s", cmd.TaskName)))
	return nil
}
func (cmd *StateHandler) runTask(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet(fmt.Sprintf("v1/state/tasks/info/%s", cmd.TaskName)))
	return nil
}
func (cmd *StateHandler) runTasks(c *kingpin.ParseContext) error {
	PrintJSON(HTTPGet("v1/state/tasks"))
	return nil
}

func HandleStateSection(app *kingpin.Application) {
	// state <framework_id, status, task, tasks>
	cmd := &StateHandler{}
	state := app.Command("state", "View persisted state")

	state.Command("framework_id", "Display the mesos framework ID").Action(cmd.runFrameworkId)

	status := state.Command("status", "Display the TaskStatus for a task name").Action(cmd.runStatus)
	status.Arg("name", "Name of the task to display").Required().StringVar(&cmd.TaskName)

	task := state.Command("task", "Display the TaskInfo for a task name").Action(cmd.runTask)
	task.Arg("name", "Name of the task to display").Required().StringVar(&cmd.TaskName)

	state.Command("tasks", "List names of all persisted tasks").Action(cmd.runTasks)
}
