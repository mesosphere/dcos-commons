package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type stateHandler struct {
	PropertyName string
}

func (cmd *stateHandler) handleFrameworkID(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/state/frameworkId"))
	return nil
}
func (cmd *stateHandler) handleProperties(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/state/properties"))
	return nil
}
func (cmd *stateHandler) handleProperty(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet(fmt.Sprintf("v1/state/properties/%s", cmd.PropertyName)))
	return nil
}
func (cmd *stateHandler) handleRefreshCache(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServicePut("v1/state/refresh"))
	return nil
}

// HandleStateSection adds state subcommands to the passed in kingpin.Application.
func HandleStateSection(app *kingpin.Application) {
	// state <framework_id, status, task, tasks>
	cmd := &stateHandler{}
	state := app.Command("state", "View persisted state")

	state.Command("framework_id", "Display the Mesos framework ID").Action(cmd.handleFrameworkID)

	state.Command("properties", "List names of all custom properties").Action(cmd.handleProperties)

	task := state.Command("property", "Display the content of a specified property").Action(cmd.handleProperty)
	task.Arg("name", "Name of the property to display").Required().StringVar(&cmd.PropertyName)

	state.Command("refresh_cache", "Refresh the state cache, used for debugging").Action(cmd.handleRefreshCache)
}
