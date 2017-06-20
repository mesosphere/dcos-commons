package main

import (
	"os"
	"os/exec"
	"fmt"

	"github.com/mesosphere/dcos-commons/cli"
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

func (cmd *CockroachHandler) sql(c *kingpin.ParseContext) error {
	runCommand("task",
		"exec",
		"-it",
		"cockroachdb-1-node-join",
		"./cockroach",
		"sql",
		"--insecure",
		"--host=internal.cockroachdb.l4lb.thisdcos.directory")
	return nil
}

func runCommand(arg ...string) {
	cmd := exec.Command("dcos", arg...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	if err != nil {
		fmt.Printf("Error: %s\n", err)
	}
}

func handleCockroachSection(app *kingpin.Application) {
	cmd := &CockroachHandler{}
	cockroach := app.Command("cockroach", "CockroachDB CLI")

	sql := cockroach.Command("sql", "Equivalent to running `cockroach sql <command>` on task.").Action(cmd.sql)
	sql.Arg("command", "CockroachDB sql command to run.").StringVar(&cmd.command)
}
