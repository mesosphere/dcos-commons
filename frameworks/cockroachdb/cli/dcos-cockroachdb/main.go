package main

import (
	"fmt"
	"strings"

	"github.com/mesosphere/dcos-commons/cli"
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

func main() {
	app := cli.New()

	cli.HandleDefaultSections(app)

	handleCockroachSection(app)

	kingpin.MustParse(app.Parse(cli.GetArguments()))
}

type CockroachHandler struct {
	command string
}

func (cmd *CockroachHandler) node(c *kingpin.ParseContext) error {
	outBytes, err := client.RunCLICommand("task",
			"exec",
			"cockroachdb-1-node-join",
			"./cockroach",
			"node",
			"--insecure",
			"--host=internal.cockroachdb.l4lb.thisdcos.directory",
			fmt.Sprintf("%s", cmd.command))
	fmt.Printf("%s\n", outBytes)
	if err != nil {
		if strings.Contains(err.Error(), "Cannot find a task with ID") {
			fmt.Printf("Failed to locate CockroachDB binary. Has the package finished installing yet?\n")
		} else {
			fmt.Printf("Error: %s\n", err)
		}
	}
	return nil
}

func (cmd *CockroachHandler) sql(c *kingpin.ParseContext) error {
	outBytes, err := client.RunCLICommand("task",
			"exec",
			"cockroachdb-1-node-join",
			"./cockroach",
			"sql",
			"--insecure",
			"--host=internal.cockroachdb.l4lb.thisdcos.directory",
			"-e",
			fmt.Sprintf("%s", cmd.command))
	fmt.Printf("%s\n", outBytes)
	if err != nil {
		if strings.Contains(err.Error(), "Cannot find a task with ID") ||
		   strings.Contains(err.Error(), "No such file or directory") {
			fmt.Printf("Failed to locate CockroachDB binary. Has the package finished installing yet?\n")
		} else {
			fmt.Printf("Error: %s\n", err)
		}
	}
	return nil
}

func handleCockroachSection(app *kingpin.Application) {
	cmd := &CockroachHandler{}
	cockroach := app.Command("cockroach", "CockroachDB CLI")

	sql := cockroach.Command("sql", "Equivalent to running `cockroach sql <command>` on task.").Action(cmd.sql)
	sql.Arg("command", "CockroachDB sql command to run.").StringVar(&cmd.command)

	node := cockroach.Command("node", "Equivalent to running `cockroach node <command>` on task.").Action(cmd.node)
	node.Arg("command", "CockroachDB node command to run.").StringVar(&cmd.command)
}
