package main

import (
	"fmt"
	"github.com/mesosphere/dcos-commons/cli"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
	"net/url"
	"os"
	"strings"
)

func main() {
	modName, err := cli.GetModuleName()
	if err != nil {
		log.Fatalf(err.Error())
	}

	app, err := cli.NewApp(
		"0.1.0",
		"Mesosphere",
		fmt.Sprintf("Deploy and manage %s clusters", strings.Title(modName)))
	if err != nil {
		log.Fatalf(err.Error())
	}

	handleNodeSection(app, modName)
	cli.HandleCommonArgs(
		app,
		modName,
		fmt.Sprintf("%s DC/OS CLI Module", strings.Title(modName)),
		[]string{"hdfs-site.xml", "core-site.xml"})

	// Omit modname:
	kingpin.MustParse(app.Parse(os.Args[2:]))
}

type NodeHandler struct {
	name string
}
func (cmd *NodeHandler) runReplace(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("name", cmd.name)
	cli.HTTPPutQuery("v1/nodes/replace", query.Encode())
	return nil
}
func (cmd *NodeHandler) runRestart(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("name", cmd.name)
	cli.HTTPPutQuery("v1/nodes/restart", query.Encode())
	return nil
}
func handleNodeSection(app *kingpin.Application, modName string) {
	cmd := &NodeHandler{}
	stateCmd := &cli.StateHandler{}

	node := app.Command("node", fmt.Sprintf("Manage %s nodes", modName))

	node.Command("list", "Lists all nodes").Action(stateCmd.RunTasks)

	replace := node.Command("replace", "Replaces a single task job, moving it to a different agent").Action(cmd.runReplace)
	replace.Arg("node", "The task name to replace").StringVar(&cmd.name)

	restart := node.Command("restart", "Restarts a single task job, keeping it on the same agent").Action(cmd.runRestart)
	restart.Arg("node", "The task name to restart").StringVar(&cmd.name)
}
