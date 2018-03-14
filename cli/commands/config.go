package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/config"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type deprecatedConfigHandler struct {
	ShowID string
}

func (cmd *deprecatedConfigHandler) handleConfigList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug config list' instead", config.ModuleName)
}

func (cmd *deprecatedConfigHandler) handleConfigShow(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug config show' instead", config.ModuleName)
}

func (cmd *deprecatedConfigHandler) handleConfigTarget(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug config target' instead", config.ModuleName)
}

func (cmd *deprecatedConfigHandler) handleConfigTargetID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug config target_id' instead", config.ModuleName)
}

// TODO(nickbp): Remove this at some point. For now it's not hurting anything because the commands are no longer shown in the help output.
func HandleDeprecatedConfigSection(app *kingpin.Application) {
	// config <list, show, target, target_id>
	cmd := &deprecatedConfigHandler{}
	config := app.Command("config", "(DEPRECATED, use 'debug config') View persisted configurations").Hidden()

	config.Command("list", "(DEPRECATED, use 'debug config list') List IDs of all available configurations").Hidden().Action(cmd.handleConfigList)

	show := config.Command("show", "(DEPRECATED, use 'debug config show') Display a specified configuration").Hidden().Action(cmd.handleConfigShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.ShowID)

	config.Command("target", "(DEPRECATED, use 'debug config target') Display the target configuration").Hidden().Action(cmd.handleConfigTarget)

	config.Command("target_id", "(DEPRECATED, use 'debug config target_id') Display ID of the target configuration").Hidden().Action(cmd.handleConfigTargetID)
}
