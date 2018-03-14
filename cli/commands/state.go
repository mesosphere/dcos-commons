package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/config"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type deprecatedStateHandler struct {
	PropertyName string
}

func (cmd *deprecatedStateHandler) handleStateFrameworkID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug state framework_id' instead", config.ModuleName)
}
func (cmd *deprecatedStateHandler) handleStateProperties(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug state properties' instead", config.ModuleName)
}
func (cmd *deprecatedStateHandler) handleStateProperty(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug state property' instead", config.ModuleName)
}
func (cmd *deprecatedStateHandler) handleStateRefreshCache(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return fmt.Errorf("This command is deprecated, use 'dcos %s debug state refresh_cache' instead", config.ModuleName)
}

// TODO(nickbp): Remove this at some point. For now it's not hurting anything because the commands are no longer shown in the help output.
func HandleDeprecatedStateSection(app *kingpin.Application) {
	// state <framework_id, status, task, tasks>
	cmd := &deprecatedStateHandler{}
	state := app.Command("state", "(DEPRECATED, use 'state config') View persisted state").Hidden()

	state.Command("framework_id", "(DEPRECATED, use 'debug state framework_id') Display the Mesos framework ID").Hidden().Action(cmd.handleStateFrameworkID)

	state.Command("properties", "(DEPRECATED, use 'debug state properties') List names of all custom properties").Hidden().Action(cmd.handleStateProperties)

	task := state.Command("property", "(DEPRECATED, use 'debug state property') Display the content of a specified property").Hidden().Action(cmd.handleStateProperty)
	task.Arg("name", "Name of the property to display").Required().StringVar(&cmd.PropertyName)

	state.Command("refresh_cache", "(DEPRECATED, use 'debug state refresh_cache') Refresh the state cache, used for debugging").Hidden().Action(cmd.handleStateRefreshCache)
}
