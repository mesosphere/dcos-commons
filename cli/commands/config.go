package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type debugConfigHandler struct {
	ShowID string
}

func (cmd *debugConfigHandler) handleDebugConfigList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/configurations")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *debugConfigHandler) handleDebugConfigShow(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet(fmt.Sprintf("v1/configurations/%s", cmd.ShowID))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *debugConfigHandler) handleDebugConfigTarget(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/configurations/target")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *debugConfigHandler) handleDebugConfigTargetID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	// TODO: figure out KingPin's error handling
	body, err := client.HTTPServiceGet("v1/configurations/targetId")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

// TODO: deprecated -> commands/debug.go, handlers ^^ should remain here to organize the debug sub-command tree, seealso commands/state.go
// HandleConfigSection adds config subcommands to the passed in kingpin.Application.
func HandleConfigSection(app *kingpin.Application) {
	// config <list, show, target, target_id>
	cmd := &debugConfigHandler{}
	config := app.Command("config", "[Deprecated (TBR 1.11, -> debug config)] View persisted configurations")

	config.Command("list", "[Deprecated (TBR 1.11, -> debug config list)]List IDs of all available configurations").Action(cmd.handleDebugConfigList)

	show := config.Command("show", "[Deprecated (TBR 1.11, -> debug config show)] Display a specified configuration").Action(cmd.handleDebugConfigShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.ShowID)

	config.Command("target", "[Deprecated (TBR 1.11, -> debug config target)] Display the target configuration").Action(cmd.handleDebugConfigTarget)

	config.Command("target_id", "[Deprectaed (TBR 1.11, -> debug config target_id)] List ID of the target configuration").Action(cmd.handleDebugConfigTargetID)
}
