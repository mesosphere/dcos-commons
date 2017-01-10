package main

import (
	"fmt"
	"github.com/mesosphere/dcos-commons/cli"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
	"strings"
)

func main() {
	modName, err := cli.GetModuleName()
	if err != nil {
		log.Fatalf(err.Error())
	}

	app, err := cli.NewApp("0.1.0", "Mesosphere", "Provides an example for DC/OS service developers")
	if err != nil {
		log.Fatalf(err.Error())
	}

	cli.HandleCommonFlags(app, modName, "Example DC/OS CLI Module")
	cli.HandleConfigSection(app)
	cli.HandleEndpointsSection(app)
	cli.HandlePlanSection(app)
	cli.HandlePodsSection(app)
	handleExampleSection(app)

	// Omit modname:
	kingpin.MustParse(app.Parse(cli.GetArguments()))
}

type ExampleCommand struct {
	echoText        []string
	echoOmitNewline bool
}

func (cmd *ExampleCommand) runEcho(c *kingpin.ParseContext) error {
	if cmd.echoOmitNewline {
		fmt.Printf(strings.Join(cmd.echoText, " "))
	} else {
		fmt.Printf("%s\n", strings.Join(cmd.echoText, " "))
	}
	return nil
}

func handleExampleSection(app *kingpin.Application) {
	cmd := &ExampleCommand{}
	example := app.Command("example", "Example custom commands")

	// 'example echo -n <text>'
	echo := example.Command("echo", "Echos some text").Action(cmd.runEcho)
	echo.Arg("text", "What to echo").StringsVar(&cmd.echoText)
	echo.Flag("no-newline", "Omit newline").Short('n').BoolVar(&cmd.echoOmitNewline)
}
