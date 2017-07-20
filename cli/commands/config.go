package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type configHandler struct {
	ShowID string
}

func (cmd *configHandler) handleList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/configurations")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *configHandler) handleShow(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet(fmt.Sprintf("v1/configurations/%s", cmd.ShowID))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *configHandler) handleTarget(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/configurations/target")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *configHandler) handleTargetID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/configurations/targetId")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
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
