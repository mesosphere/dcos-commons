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

	app, err := cli.NewApp("0.1.0", "Mesosphere", fmt.Sprintf("Deploy and manage %s clusters", strings.Title(modName)))
	if err != nil {
		log.Fatalf(err.Error())
	}

	cli.HandleCommonFlags(app, modName, fmt.Sprintf("%s DC/OS CLI Module", strings.Title(modName)))
	cli.HandleConfigSection(app)
	cli.HandleEndpointsSection(app)
	cli.HandlePlanSection(app)
	cli.HandlePodsSection(app)
	cli.HandleStateSection(app)

	// Omit modname:
	kingpin.MustParse(app.Parse(cli.GetArguments()))
}
