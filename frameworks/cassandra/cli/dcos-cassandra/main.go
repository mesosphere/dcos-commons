package main

import (
	"github.com/mesosphere/dcos-commons/cli"
	"gopkg.in/alecthomas/kingpin.v2"
)

func main() {
	app := cli.New()
	cli.HandleDefaultSections(app)

	kingpin.MustParse(app.Parse(cli.GetArguments()))
}
