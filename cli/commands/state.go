package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type debugStateHandler struct {
	PropertyName string
}

func (cmd *debugStateHandler) handleConfigStateFrameworkID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/state/frameworkId")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}
func (cmd *debugStateHandler) handleConfigStateProperties(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/state/properties")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)

	return nil
}
func (cmd *debugStateHandler) handleConfigStateProperty(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet(fmt.Sprintf("v1/state/properties/%s", cmd.PropertyName))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}
func (cmd *debugStateHandler) handleConfigStateRefreshCache(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServicePut("v1/state/refresh")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

// TODO: deprecated -> commands/debug.go, handlers ^^ should remain here to organize the debug sub-command tree, seealso commands/config.go
// HandleStateSection adds state subcommands to the passed in kingpin.Application.
func HandleStateSection(app *kingpin.Application) {
	// state <framework_id, status, task, tasks>
	cmd := &debugStateHandler{}
	state := app.Command("state", "[Deprecated (TBR 1.11, -> debug state)] View persisted state")

	state.Command("framework_id", "[Deprecated (TBR 1.11, -> debug state framework_id)] Display the Mesos framework ID").Action(cmd.handleConfigStateFrameworkID)

	state.Command("properties", "[Deprecated (TBR 1.11, -> debug state properties)] List names of all custom properties").Action(cmd.handleConfigStateProperties)

	task := state.Command("property", "[Deprecated (TBR 1.11, -> debug state property)] Display the content of a specified property").Action(cmd.handleConfigStateProperty)
	task.Arg("name", "Name of the property to display").Required().StringVar(&cmd.PropertyName)

	state.Command("refresh_cache", "[Deprecated (TBR 1.11, -> debug state refresh_cache)] Refresh the state cache, used for debugging").Action(cmd.handleConfigStateRefreshCache)
}
