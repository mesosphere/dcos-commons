package commands

import (
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

// HandleDebugSection adds config subcommands to the passed in kingpin.Application.
func HandleDebugSection(app *kingpin.Application) {
	debug := app.Command("debug", "View service state useful in debugging")
	HandleDebugConfigSection(app, debug)
	HandleDebugStateSection(app, debug)
}

func HandleDebugConfigSection(app *kingpin.Application, debug *kingpin.CmdClause) {
	// config <list, show, target, target_id>
	cmd := &debugConfigHandler{}

	config := debug.Command("config", "View persisted configurations")

	config.Command("list", "List IDs of all available configurations").Action(cmd.handleDebugConfigList)

	show := config.Command("show", "Display a specified configuration").Action(cmd.handleDebugConfigShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.ShowID)

	config.Command("target", "Display the target configuration").Action(cmd.handleDebugConfigTarget)

	config.Command("target_id", "List ID of the target configuration").Action(cmd.handleDebugConfigTargetID)
}

func HandleDebugStateSection(app *kingpin.Application, debug *kingpin.CmdClause) {
	// state <framework_id, status, task, tasks>
	cmd := &debugStateHandler{}

	state := debug.Command("state", "View persisted state")

	state.Command("framework_id", "Display the Mesos framework ID").Action(cmd.handleConfigStateFrameworkID)

	state.Command("properties", "List names of all custom properties").Action(cmd.handleConfigStateProperties)

	task := state.Command("property", "Display the content of a specified property").Action(cmd.handleConfigStateProperty)
	task.Arg("name", "Name of the property to display").Required().StringVar(&cmd.PropertyName)

	state.Command("refresh_cache", "Refresh the state cache, used for debugging").Action(cmd.handleConfigStateRefreshCache)
}
