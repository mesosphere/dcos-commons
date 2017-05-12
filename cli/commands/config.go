package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type ConfigHandler struct {
	ShowId string
}

func (cmd *ConfigHandler) RunList(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/configurations"))
	return nil
}
func (cmd *ConfigHandler) RunShow(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet(fmt.Sprintf("v1/configurations/%s", cmd.ShowId)))
	return nil
}
func (cmd *ConfigHandler) RunTarget(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/configurations/target"))
	return nil
}
func (cmd *ConfigHandler) RunTargetId(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/configurations/targetId"))
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
