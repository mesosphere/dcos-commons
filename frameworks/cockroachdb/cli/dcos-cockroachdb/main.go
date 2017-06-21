package main

import (
	"os"
	"os/exec"
	"fmt"

	"github.com/mesosphere/dcos-commons/cli"
	"github.com/mesosphere/dcos-commons/cli/config"
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
	cockroachHostFlag := fmt.Sprintf("--host=internal.%s.l4lb.thisdcos.directory", config.ServiceName)
	cockroachTask := fmt.Sprintf("%s-1-node-join", config.ServiceName)
	dcosCmd = append(dcosCmd,
		"task",
		"exec",
		"-it",
		cockroachTask,
		"./cockroach",
		"sql",
		"--insecure",
		cockroachHostFlag)
	if cmd.database != "" {
		dcosCmd = append(dcosCmd, "-d", cmd.database)
	}
	if cmd.user != "" {
		dcosCmd = append(dcosCmd, "-u", cmd.user)
	}
	runDcosCommand(dcosCmd...)
	return nil
}

func runDcosCommand(arg ...string) {
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
