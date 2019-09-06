package main

import (
	"fmt"
	"github.com/mesosphere/dcos-commons/cli"
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
	"os"
	"strings"
)

func main() {
	app := cli.New()
	arguments := cli.GetArguments()

	cli.HandleDefaultSections(app)

	hdfsCliCommandIndex := HandleHdfsSection(app, arguments)

	if hdfsCliCommandIndex != -1 {
		// Parse only arguments before `hdfs` CLI command.
		// Subcommands and flags after `hdfs` command belongs to HDFS CLI
		kingpin.MustParse(app.Parse(arguments[:hdfsCliCommandIndex+1]))
	} else {
		kingpin.MustParse(app.Parse(arguments))
	}
}

func HandleHdfsSection(app *kingpin.Application, arguments []string) int {
	const hdfs = "hdfs"

	cmd := app.Command(hdfs, "Run HDFS CLI command")

	i := firstArgumentIndex(hdfs, arguments)
	if i != -1 {
		hdfsCliArgs := &HdfsCliArgs{}
		hdfsCliArgs.arguments = arguments[i+1:]
		cmd.Action(hdfsCliArgs.runHdfs)
	}
	return i
}

// linear search for the first argument
// if equal target command return index
func firstArgumentIndex(targetCommand string, arguments []string) int {
	for i, v := range arguments {
		if !strings.HasPrefix(v, "-") { // ignore flags
			if targetCommand == v {
				return i
			} else {
				return -1
			}
		}
	}
	return -1
}

type HdfsCliArgs struct {
	arguments []string
}

func (args *HdfsCliArgs) String() string {
	return strings.Join(args.arguments, " ")
}

func (args *HdfsCliArgs) runHdfs(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	dcosExecCommand := []string{"task", "exec", "name-0-node", "bash", "-c"}
	hdfsCommand := fmt.Sprintf("export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jdk*/); $HDFS_VERSION/bin/hdfs %s", args.String())
	fullCommand := append(dcosExecCommand, hdfsCommand)

	value, error := client.RunCLICommand(fullCommand...)
	if error != nil {
		client.PrintMessage(error.Error())
		os.Exit(1)
	}

	client.PrintMessage(value)
	return nil
}
