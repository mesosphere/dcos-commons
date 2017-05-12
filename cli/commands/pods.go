package commands

import (
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

// Pods section

type PodsHandler struct {
	PodName string
}

func (cmd *PodsHandler) RunList(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet("v1/pods"))
	return nil
}
func (cmd *PodsHandler) RunStatus(c *kingpin.ParseContext) error {
	if len(cmd.PodName) == 0 {
		client.PrintJSON(client.HTTPServiceGet("v1/pods/status"))
	} else {
		client.PrintJSON(client.HTTPServiceGet(fmt.Sprintf("v1/pods/%s/status", cmd.PodName)))
	}
	return nil
}
func (cmd *PodsHandler) RunInfo(c *kingpin.ParseContext) error {
	client.PrintJSON(client.HTTPServiceGet(fmt.Sprintf("v1/pods/%s/info", cmd.PodName)))
	return nil
}
func (cmd *PodsHandler) RunRestart(c *kingpin.ParseContext) error {
	client.PrintResponseText(client.HTTPServicePost(fmt.Sprintf("v1/pods/%s/restart", cmd.PodName)))
	return nil
}
func (cmd *PodsHandler) RunReplace(c *kingpin.ParseContext) error {
	client.PrintResponseText(client.HTTPServicePost(fmt.Sprintf("v1/pods/%s/replace", cmd.PodName)))
	return nil
}

func HandlePodsSection(app *kingpin.Application) {
	// pods [status [name], info <name>, restart <name>, replace <name>]
	cmd := &PodsHandler{}
	pods := app.Command("pods", "View Pod/Task state")

	pods.Command("list", "Display the list of known pod instances").Action(cmd.RunList)

	status := pods.Command("status", "Display the status for tasks in one pod or all pods").Action(cmd.RunStatus)
	status.Arg("pod", "Name of a specific pod instance to display").StringVar(&cmd.PodName)

	info := pods.Command("info", "Display the full state information for tasks in a pod").Action(cmd.RunInfo)
	info.Arg("pod", "Name of the pod instance to display").Required().StringVar(&cmd.PodName)

	restart := pods.Command("restart", "Restarts a given pod without moving it to a new agent").Action(cmd.RunRestart)
	restart.Arg("pod", "Name of the pod instance to restart").Required().StringVar(&cmd.PodName)

	replace := pods.Command("replace", "Destroys a given pod and moves it to a new agent").Action(cmd.RunReplace)
	replace.Arg("pod", "Name of the pod instance to replace").Required().StringVar(&cmd.PodName)
}
