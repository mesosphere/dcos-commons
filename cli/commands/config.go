package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type configHandler struct {
	ShowID string
}

func (cmd *configHandler) handleList(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/configurations"))
	return nil
}

func (cmd *configHandler) handleShow(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet(fmt.Sprintf("v1/configurations/%s", cmd.ShowID)))
	return nil
}

func (cmd *configHandler) handleTarget(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/configurations/target"))
	return nil
}

func (cmd *configHandler) handleTargetID(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/configurations/targetId"))
	return nil
}

// HandleConfigSection adds config subcommands to the passed in kingpin.Application.
func HandleConfigSection(app *kingpin.Application) {
	// config <list, show, target, target_id>
	cmd := &configHandler{}
	config := app.Command("config", "View persisted configurations")

	config.Command("list", "List IDs of all available configurations").Action(cmd.handleList)

	show := config.Command("show", "Display a specified configuration").Action(cmd.handleShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.ShowID)

	config.Command("target", "Display the target configuration").Action(cmd.handleTarget)

	config.Command("target_id", "List ID of the target configuration").Action(cmd.handleTargetID)
}
