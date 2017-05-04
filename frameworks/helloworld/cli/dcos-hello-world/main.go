package main

import (
	"github.com/mesosphere/dcos-commons/cli"
    "github.com/mesosphere/dcos-commons/cli/commands"
	"gopkg.in/alecthomas/kingpin.v2"
)

func main() {
	app := cli.New()

	commands.HandleConfigSection(app)
	commands.HandleEndpointsSection(app)
	commands.HandlePlanSection(app)
	commands.HandlePodsSection(app)
	commands.HandleStateSection(app)

	kingpin.MustParse(app.Parse(cli.GetArguments()))
}
