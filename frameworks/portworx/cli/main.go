package main

import (
	"github.com/mesosphere/dcos-commons/cli"
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

func main() {
	app := cli.New()

	cli.HandleDefaultSections(app)

	handlePxSection(app)

	kingpin.MustParse(app.Parse(cli.GetArguments()))
}

func runNodeList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	response, err := client.HTTPServiceGet("v1/px/status")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	} else {
		client.PrintJSONBytes(response)
	}
	return nil
}

func runVolumeList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	response, err := client.HTTPServiceGet("v1/px/volumes")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	} else {
		client.PrintJSONBytes(response)
	}
	return nil
}

func handlePxSection(app *kingpin.Application) {

	app.Command("status", "List the status of the nodes in the PX cluster").
		Action(runNodeList)

	volume := app.Command("volume", "Manage volumes")
	volume.Command("list", "List the volumes").
		Action(runVolumeList)
}
