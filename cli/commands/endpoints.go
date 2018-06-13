package commands

import (
	"github.com/mesosphere/dcos-commons/cli/queries"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type endpointsHandler struct {
	q    *queries.Endpoints
	name string
}

func (cmd *endpointsHandler) handleEndpoints(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	if len(cmd.name) == 0 {
		return cmd.q.List()
	} else {
		return cmd.q.Show(cmd.name)
	}
}

// HandleEndpointsSection adds endpoint subcommands to the provided kingpin.Application.
func HandleEndpointsSection(app *kingpin.Application, q *queries.Endpoints) {
	HandleEndpointsCommands(app.Command("endpoints", "View client endpoints").Alias("endpoint"), q)
}

// HandleEndpointsCommand adds the endpoint subcommands to the provided kingpin.CmdClause.
func HandleEndpointsCommands(endpoints *kingpin.CmdClause, q *queries.Endpoints) {
	// endpoint[s] [type]
	cmd := &endpointsHandler{q: q}
	endpointsCmd := endpoints.Action(cmd.handleEndpoints)
	endpointsCmd.Arg("name", "Name of specific endpoint to be returned").StringVar(&cmd.name)
}
