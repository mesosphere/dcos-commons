package main

import (
	"github.com/mesosphere/dcos-commons/cli"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
)

func main() {
	modName, err := cli.GetModuleName()
	if err != nil {
		log.Fatalf(err.Error())
	}

	app, err := cli.NewApp("0.1.0", "Mesosphere", "Manage Elastic framework")
	if err != nil {
		log.Fatalf(err.Error())
	}

	cli.HandleCommonFlags(app, modName, "Elastic CLI")
	cli.HandleConfigSection(app)
	cli.HandleEndpointsSection(app)
	cli.HandlePlanSection(app)
	cli.HandlePodsSection(app)
	cli.HandleStateSection(app)

	// Omit modname:
	kingpin.MustParse(app.Parse(cli.GetArguments()))
}
