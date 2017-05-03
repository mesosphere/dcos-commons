
// State section

type StateHandler struct {
    PropertyName string
}

func (cmd *StateHandler) RunFrameworkId(c *kingpin.ParseContext) error {
    PrintJSON(HTTPGet("v1/state/frameworkId"))
    return nil
}
func (cmd *StateHandler) RunProperties(c *kingpin.ParseContext) error {
    PrintJSON(HTTPGet("v1/state/properties"))
    return nil
}
func (cmd *StateHandler) RunProperty(c *kingpin.ParseContext) error {
    PrintJSON(HTTPGet(fmt.Sprintf("v1/state/properties/%s", cmd.PropertyName)))
    return nil
}
func (cmd *StateHandler) RunRefreshCache(c *kingpin.ParseContext) error {
    PrintJSON(HTTPPut("v1/state/refresh"))
    return nil
}

func HandleStateSection(app *kingpin.Application) {
    // state <framework_id, status, task, tasks>
    cmd := &StateHandler{}
    state := app.Command("state", "View persisted state")

    state.Command("framework_id", "Display the mesos framework ID").Action(cmd.RunFrameworkId)

    state.Command("properties", "List names of all custom properties").Action(cmd.RunProperties)

    task := state.Command("property", "Display the content of a specified property").Action(cmd.RunProperty)
    task.Arg("name", "Name of the property to display").Required().StringVar(&cmd.PropertyName)

    state.Command("refresh_cache", "Refresh the state cache, used for debugging").Action(cmd.RunRefreshCache)
}