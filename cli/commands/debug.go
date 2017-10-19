package commands

import (
	"encoding/json"
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

// HandleDebugSection adds config subcommands to the passed in kingpin.Application.
func HandleDebugSection(app *kingpin.Application) {
	debug := app.Command("debug", "View service state useful in debugging")
	HandleDebugConfigSection(app, debug)
	HandleDebugStateSection(app, debug)
	HandleDebugPodSection(app, debug)
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

type debugPodHandler struct {
	PodName string
	TaskNames []string
}

func (cmd *debugPodHandler) handleStop(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	cmd.stopOrStart("stop")
	return nil
}

func (cmd *debugPodHandler) handleStart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	cmd.stopOrStart("start")
	return nil
}

func (cmd *debugPodHandler) stopOrStart(podCmd string) {
	var err error
	var body []byte
	if len(cmd.TaskNames) == 0 {
		body, err = client.HTTPServicePost(fmt.Sprintf("v1/pod/%s/%s", cmd.PodName, podCmd))
	} else {
		var payload []byte
		payload, err = json.Marshal(cmd.TaskNames)
		if err != nil {
			client.PrintMessageAndExit(err.Error())
		}
		body, err = client.HTTPServicePostJSON(fmt.Sprintf("v1/pod/%s/%s", cmd.PodName, podCmd), string(payload))
	}
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
}

func HandleDebugPodSection(app *kingpin.Application, debug *kingpin.CmdClause) {
	// pod <stop, start>
	cmd := &debugPodHandler{}

	pod := debug.Command("pod", "Access pods")

	stop := pod.Command("stop", "Stops a pod's tasks for debugging").Action(cmd.handleStop)
	stop.Arg("pod", "Name of the pod instance to stop").Required().StringVar(&cmd.PodName)
	stop.Flag("tasks", "List of specific tasks to be stopped, otherwise the entire pod").Short('t').StringsVar(&cmd.TaskNames)

	start := pod.Command("start", "Resumes a pod's normal execution following a stop command").Action(cmd.handleStart)
	start.Arg("pod", "Name of the pod instance to replace").Required().StringVar(&cmd.PodName)
	start.Flag("tasks", "List of specific tasks to be started, otherwise the entire pod").Short('t').StringsVar(&cmd.TaskNames)
}
