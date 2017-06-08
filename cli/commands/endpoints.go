package commands

import (
	"log"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
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
	response := client.HTTPServiceGet(path)
	if len(cmd.Name) == 0 {
		// Root endpoint: Always produce JSON
		client.PrintJSON(response)
	} else {
		// Any specific endpoints: May be any format, so just print the raw text
		client.PrintResponseText(response)
	}
	return nil
}

func HandleEndpointsSection(app *kingpin.Application) {
	// endpoint[s] [type]
	cmd := &EndpointsHandler{}
	endpoints := app.Command("endpoints", "View client endpoints").Alias("endpoint").Action(cmd.RunEndpoints)
	endpoints.Arg("name", "Name of specific endpoint to be returned").StringVar(&cmd.Name)
}
