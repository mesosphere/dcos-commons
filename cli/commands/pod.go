package commands

import (
	"github.com/mesosphere/dcos-commons/cli/queries"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type podHandler struct {
	q       *queries.Pod
	podName string
	rawJSON bool
}

func (cmd *podHandler) handleList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.List()
}

func (cmd *podHandler) handleStatus(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Status(cmd.podName, cmd.rawJSON)
}

func (cmd *podHandler) handleInfo(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Info(cmd.podName)
}

func (cmd *podHandler) handleRestart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Command("restart", cmd.podName, []string{})
}

func (cmd *podHandler) handleReplace(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Command("replace", cmd.podName, []string{})
}

// HandlePodSection adds a pod section to the provided kingpin.Application.
func HandlePodSection(app *kingpin.Application, q *queries.Pod) {
	HandlePodCommands(app.Command("pod", "View Pod/Task state").Alias("pods"), q)
}

// HandlePodCommands adds pod subcommands to the provided kingpin.CmdClause.
func HandlePodCommands(pod *kingpin.CmdClause, q *queries.Pod) {
	// pod[s] [status [name], info <name>, restart <name>, replace <name>]
	cmd := &podHandler{q: q}

	pod.Command("list", "Display the list of known pod instances").Action(cmd.handleList)

	status := pod.Command("status", "Display the status for tasks in one pod or all pods").Action(cmd.handleStatus)
	status.Arg("pod", "Name of a specific pod instance to display").StringVar(&cmd.podName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.rawJSON)

	info := pod.Command("info", "Display the full state information for tasks in a pod").Action(cmd.handleInfo)
	info.Arg("pod", "Name of the pod instance to display").Required().StringVar(&cmd.podName)

	restart := pod.Command("restart", "Restarts a given pod without moving it to a new agent").Action(cmd.handleRestart)
	restart.Arg("pod", "Name of the pod instance to restart").Required().StringVar(&cmd.podName)

	replace := pod.Command("replace", "Destroys a given pod and moves it to a new agent").Action(cmd.handleReplace)
	replace.Arg("pod", "Name of the pod instance to replace").Required().StringVar(&cmd.podName)
}
