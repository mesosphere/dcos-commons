
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
    response := HTTPGet(path)
    if len(cmd.Name) == 0 {
        // Root endpoint: Always produce JSON
        PrintJSON(response)
    } else {
        // Any specific endpoints: May be any format, so just print the raw text
        PrintText(response)
    }
    return nil
}

func HandleEndpointsSection(app *kingpin.Application) {
    // endpoints [type]
    cmd := &EndpointsHandler{}
    endpoints := app.Command("endpoints", "View client endpoints").Action(cmd.RunEndpoints)
    // TODO(nickbp): Remove deprecated argument after April 2017:
    endpoints.Flag("native", "deprecated argument").BoolVar(&cmd.PrintDeprecatedNotice)
    endpoints.Arg("name", "Name of specific endpoint to be returned").StringVar(&cmd.Name)
}
