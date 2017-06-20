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

type SQLHandler struct {
	database string
	user string
}

func (cmd *SQLHandler) sql(c *kingpin.ParseContext) error {
	var dcosCmd []string;
	dcosCmd = append(dcosCmd,
		"task",
		"exec",
		"-it",
		"cockroachdb-1-node-join",
		"./cockroach",
		"sql",
		"--insecure",
		"--host=internal.cockroachdb.l4lb.thisdcos.directory")
	if cmd.database != "" {
		dcosCmd = append(dcosCmd, "-d", cmd.database)
	}
	if cmd.user != "" {
		dcosCmd = append(dcosCmd, "-u", cmd.user)
	}
	runCommand(dcosCmd...)
	return nil
}

func runCommand(arg ...string) {
	cmd := exec.Command("dcos", arg...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	if err != nil {
		fmt.Printf("[err] %s\n", err)
	}
}

func handleCockroachSection(app *kingpin.Application) {
	cmd := &SQLHandler{}
	sql := app.Command("sql", "Opens interactive Cockroachdb SQL shell").Action(cmd.sql)
	sql.Flag("database", "The database to connect to.").Short('d').StringVar(&cmd.database)
	sql.Flag("user", "The user connecting to the database. The user must have privileges for any statement executed.").Short('u').StringVar(&cmd.user)
}
