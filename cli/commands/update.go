package commands

import (
	"github.com/mesosphere/dcos-commons/cli/queries"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type describeHandler struct {
	q *queries.Package
}

func (cmd *describeHandler) handleDescribe(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Describe()
}

// HandleDescribeSection adds the describe subcommands to the provided kingpin.Application.
func HandleDescribeSection(app *kingpin.Application, q *queries.Package) {
	HandleDescribeCommands(app.Command("describe", "View the configuration for this service"), q)
}

// HandleUpdateCommands adds the describe subcommands to the provided kingpin.CmdClause.
func HandleDescribeCommands(describe *kingpin.CmdClause, q *queries.Package) {
	cmd := &describeHandler{q: q}
	describe.Action(cmd.handleDescribe)
}

type updateHandler struct {
	q              *queries.Package
	optionsFile    string
	packageVersion string
	replace        bool
}

func (cmd *updateHandler) ViewPackageVersions(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.VersionInfo()
}

func (cmd *updateHandler) UpdateConfiguration(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Update(cmd.optionsFile, cmd.packageVersion, cmd.replace)
}

// HandleUpdateSection adds the update section to the provided kingpin.Application.
func HandleUpdateSection(app *kingpin.Application, packageQueries *queries.Package, planQueries *queries.Plan) {
	HandleUpdateCommands(app.Command("update", "Updates the package version or configuration for this DC/OS service"), packageQueries, planQueries)
}

// HandleUpdateCommands adds the update subcommands to the provided kingpin.CmdClause.
func HandleUpdateCommands(update *kingpin.CmdClause, packageQueries *queries.Package, planQueries *queries.Plan) {
	cmd := &updateHandler{q: packageQueries}

	start := update.Command("start", "Launches an update operation").Action(cmd.UpdateConfiguration)
	start.Flag("options", "Path to a JSON file that contains customized package installation options, or 'stdin' to read from stdin").StringVar(&cmd.optionsFile)
	start.Flag("package-version", "The desired package version").StringVar(&cmd.packageVersion)
	start.Flag("replace", "Replace the existing configuration in whole. Otherwise, the existing configuration and options are merged.").BoolVar(&cmd.replace)

	planCmd := &planHandler{q: planQueries}

	forceComplete := update.Command("force-complete", "Force complete a specific step in the provided phase").Alias("force").Action(planCmd.handleForceComplete)
	forceComplete.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&planCmd.phase)
	forceComplete.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&planCmd.step)

	forceRestart := update.Command("force-restart", "Restart update plan, or specific step in the provided phase").Alias("restart").Action(planCmd.handleForceRestart)
	forceRestart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&planCmd.phase)
	forceRestart.Arg("step", "Name or UUID of step to be restarted").StringVar(&planCmd.step)

	update.Command("package-versions", "View a list of available package versions to downgrade or upgrade to").Action(cmd.ViewPackageVersions)

	update.Command("pause", "Pause update plan").Alias("interrupt").Action(planCmd.handlePause)

	update.Command("resume", "Resume update plan").Alias("continue").Action(planCmd.handleResume)

	status := update.Command("status", "View status of a running update").Alias("show").Action(planCmd.handleStatus)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&planCmd.rawJSON)
	// ensure plan name is passed
	status.Flag("plan", "Name of the plan to launch").Default("deploy").Hidden().StringVar(&planCmd.planName)
}
