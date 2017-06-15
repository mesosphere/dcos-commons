package commands

import (
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type endpointsHandler struct {
	Name string
}

func (cmd *endpointsHandler) handleEndpoints(c *kingpin.ParseContext) error {
	path := "v1/endpoints"
	if len(cmd.Name) != 0 {
		path += "/" + cmd.Name
	}
	responseBytes, err := client.HTTPServiceGet(path)
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if len(cmd.Name) == 0 {
		// Root endpoint: Always produce JSON
		client.PrintJSONBytes(responseBytes)
	} else {
		// Any specific endpoints: May be any format, so just print the raw text
		client.PrintResponseText(responseBytes)
	}
	return nil
}

// HandleEndpointsSection adds endpoint subcommands to the passed in kingpin.Application.
func HandleEndpointsSection(app *kingpin.Application) {
	// endpoint[s] [type]
	cmd := &endpointsHandler{}
	endpoints := app.Command("endpoints", "View client endpoints").Alias("endpoint").Action(cmd.handleEndpoints)
	endpoints.Arg("name", "Name of specific endpoint to be returned").StringVar(&cmd.Name)
}
