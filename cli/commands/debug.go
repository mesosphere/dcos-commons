package commands

import (
	"github.com/mesosphere/dcos-commons/cli/queries"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

// "debug config"

type debugConfigHandler struct {
	q      *queries.Config
	showID string
}

func (cmd *debugConfigHandler) handleDebugConfigList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.List()
}

func (cmd *debugConfigHandler) handleDebugConfigShow(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Show(cmd.showID)
}

func (cmd *debugConfigHandler) handleDebugConfigTarget(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Target()
}

func (cmd *debugConfigHandler) handleDebugConfigTargetID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.TargetID()
}

func HandleDebugConfigSection(debug *kingpin.CmdClause, q *queries.Config) {
	// debug config <list, show, target, target_id>
	cmd := &debugConfigHandler{q: q}

	config := debug.Command("config", "View persisted configurations").Alias("configs")

	config.Command("list", "List IDs of all available configurations").Action(cmd.handleDebugConfigList)

	show := config.Command("show", "Display a specified configuration").Action(cmd.handleDebugConfigShow)
	show.Arg("config_id", "ID of the configuration to display").Required().StringVar(&cmd.showID)

	config.Command("target", "Display the target configuration").Action(cmd.handleDebugConfigTarget)

	config.Command("target_id", "List ID of the target configuration").Action(cmd.handleDebugConfigTargetID)
}

// "debug pod"

type debugPodHandler struct {
	q         *queries.Pod
	podName   string
	taskNames []string
}

func (cmd *debugPodHandler) handlePause(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Command("pause", cmd.podName, cmd.taskNames)
}

func (cmd *debugPodHandler) handleResume(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Command("resume", cmd.podName, cmd.taskNames)
}

func HandleDebugPodSection(debug *kingpin.CmdClause, q *queries.Pod) {
	// debug pod <pause, resume>
	cmd := &debugPodHandler{q: q}

	pod := debug.Command("pod", "Debug pods").Alias("pods")

	pause := pod.Command("pause", "Pauses a pod's tasks for debugging").Action(cmd.handlePause)
	pause.Arg("pod", "Name of the pod instance to pause").Required().StringVar(&cmd.podName)
	pause.Flag("tasks", "List of specific tasks to be paused, otherwise the entire pod").Short('t').StringsVar(&cmd.taskNames)

	resume := pod.Command("resume", "Resumes a pod's normal execution following a pause command").Action(cmd.handleResume)
	resume.Arg("pod", "Name of the pod instance to replace").Required().StringVar(&cmd.podName)
	resume.Flag("tasks", "List of specific tasks to be resumed, otherwise the entire pod").Short('t').StringsVar(&cmd.taskNames)
}

// "debug state"

type debugStateHandler struct {
	q            *queries.State
	propertyName string
}

func (cmd *debugStateHandler) handleConfigStateFrameworkID(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.FrameworkID()
}
func (cmd *debugStateHandler) handleConfigStateProperties(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.ListProperties()
}
func (cmd *debugStateHandler) handleConfigStateProperty(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Property(cmd.propertyName)

}
func (cmd *debugStateHandler) handleConfigStateRefreshCache(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.RefreshCache()
}

func HandleDebugStateSection(debug *kingpin.CmdClause, q *queries.State) {
	// debug state <framework_id, status, task, tasks>
	cmd := &debugStateHandler{q: q}

	state := debug.Command("state", "View persisted state")

	state.Command("framework_id", "Display the Mesos framework ID").Action(cmd.handleConfigStateFrameworkID)

	state.Command("properties", "List names of all custom properties").Action(cmd.handleConfigStateProperties)

	task := state.Command("property", "Display the content of a specified property").Action(cmd.handleConfigStateProperty)
	task.Arg("name", "Name of the property to display").Required().StringVar(&cmd.propertyName)

	state.Command("refresh_cache", "Refresh the state cache, used for debugging").Action(cmd.handleConfigStateRefreshCache)
}

// HandleDebugSection adds a debug section to the provided kingpin.Application.
func HandleDebugSection(app *kingpin.Application, cq *queries.Config, pq *queries.Pod, sq *queries.State) {
	HandleDebugCommands(app.Command("debug", "View service state useful in debugging"), cq, pq, sq)
}

// HandleDebugCommands adds debug subcommands to the provided kingpin.CmdClause.
func HandleDebugCommands(debug *kingpin.CmdClause, cq *queries.Config, pq *queries.Pod, sq *queries.State) {
	HandleDebugConfigSection(debug, cq)
	HandleDebugPodSection(debug, pq)
	HandleDebugStateSection(debug, sq)
}
