package commands

import (
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
)

// Endpoints section

type EndpointsHandler struct {
	Name                  string
	PrintDeprecatedNotice bool
}

func (cmd *EndpointsHandler) RunEndpoints(c *kingpin.ParseContext) error {
	// TODO(nickbp): Remove this after April 2017
	if cmd.PrintDeprecatedNotice {
		log.Fatalf("--native is no longer supported. Use 'native' entries in endpoint listing.")
	}

	path := "v1/endpoints"
	if len(cmd.Name) != 0 {
		path += "/" + cmd.Name
	}
	response := client.HTTPGet(path)
	if len(cmd.Name) == 0 {
		// Root endpoint: Always produce JSON
		client.PrintJSON(response)
	} else {
		// Any specific endpoints: May be any format, so just print the raw text
		client.PrintText(response)
	}
	return nil
}

func HandleEndpointsSection(app *kingpin.Application) {
	// endpoints [type]
	cmd := &EndpointsHandler{}
	endpoints := app.Command("endpoints", "View client endpoints").Action(cmd.RunEndpoints)
	endpoints.Arg("name", "Name of specific endpoint to be returned").StringVar(&cmd.Name)
}
