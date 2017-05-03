
// Connection section, manually implemented by some services (DEPRECATED, use common Endpoints)

type ConnectionHandler struct {
    TypeName string
}

func (cmd *ConnectionHandler) RunConnection(c *kingpin.ParseContext) error {
    if len(cmd.TypeName) == 0 {
        // Root endpoint: Always produce JSON
        PrintJSON(HTTPGet("v1/connection"))
    } else {
        // Any custom type endpoints: May be any format, so just print the raw text
        PrintText(HTTPGet(fmt.Sprintf("v1/connection/%s", cmd.TypeName)))
    }
    return nil
}

// TODO remove this command once callers have migrated to HandleEndpointsSection().
func HandleConnectionSection(app *kingpin.Application, connectionTypes []string) {
    // connection [type]
    cmd := &ConnectionHandler{}
    connection := app.Command("connection", fmt.Sprintf("View connection information (custom types: %s)", strings.Join(connectionTypes, ", "))).Action(cmd.RunConnection)
    if len(connectionTypes) != 0 {
        connection.Arg("type", fmt.Sprintf("Custom type of the connection data to display (%s)", strings.Join(connectionTypes, ", "))).StringVar(&cmd.TypeName)
    }
}
