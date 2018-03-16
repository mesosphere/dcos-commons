package commands

import (
	"github.com/mesosphere/dcos-commons/cli/queries"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type planHandler struct {
	q          *queries.Plan
	planName   string
	parameters []string
	phase      string
	step       string
	rawJSON    bool
}

func (cmd *planHandler) handleForceComplete(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.ForceComplete(cmd.planName, cmd.phase, cmd.step)
}

func (cmd *planHandler) handleForceRestart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.ForceRestart(cmd.planName, cmd.phase, cmd.step)
}

func (cmd *planHandler) handleList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.List()
}

func (cmd *planHandler) handlePause(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Pause(cmd.planName, cmd.phase)
}

func (cmd *planHandler) handleResume(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Resume(cmd.planName, cmd.phase)
}

func (cmd *planHandler) handleStart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Start(cmd.planName, cmd.parameters)
}

func (cmd *planHandler) handleStatus(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Status(cmd.planName, cmd.rawJSON)
}

func (cmd *planHandler) handleStop(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	return cmd.q.Stop(cmd.planName)
}

// HandlePlanSection adds a plan section to the passed in kingpin.Application
func HandlePlanSection(app *kingpin.Application, q *queries.Plan) {
	HandlePlanCommands(app.Command("plan", "Query service plans").Alias("plans"), q)
}

// HandlePlanCommands adds plan subcommands to the passed in kingpin.CmdClause
func HandlePlanCommands(plan *kingpin.CmdClause, q *queries.Plan) {
	// plan <active, continue, force, interrupt, restart, status/show>
	cmd := &planHandler{q: q}

	plan.Command("list", "Show all plans for this service").Action(cmd.handleList)

	status := plan.Command("status", "Display the status of the plan with the provided plan name").Alias("show").Action(cmd.handleStatus)
	status.Arg("plan", "Name of the plan to show").Required().StringVar(&cmd.planName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.rawJSON)

	start := plan.Command("start", "Start the plan with the provided name and any optional plan arguments").Action(cmd.handleStart)
	start.Arg("plan", "Name of the plan to start").Required().StringVar(&cmd.planName)
	start.Flag("params", "Envvar definition in VAR=value form; can be repeated for multiple variables").Short('p').StringsVar(&cmd.parameters)

	stop := plan.Command("stop", "Stop the running plan with the provided name").Action(cmd.handleStop)
	stop.Arg("plan", "Name of the plan to stop").Required().StringVar(&cmd.planName)

	pause := plan.Command("pause", "Pause the plan, or a specific phase in that plan with the provided phase name (or UUID)").Alias("interrupt").Action(cmd.handlePause)
	pause.Arg("plan", "Name of the plan to pause").Required().StringVar(&cmd.planName)
	pause.Arg("phase", "Name or UUID of a specific phase to pause").StringVar(&cmd.phase)

	resume := plan.Command("resume", "Resume the plan, or a specific phase in that plan with the provided phase name (or UUID)").Alias("continue").Action(cmd.handleResume)
	resume.Arg("plan", "Name of the plan to resume").Required().StringVar(&cmd.planName)
	resume.Arg("phase", "Name or UUID of a specific phase to continue").StringVar(&cmd.phase)

	forceRestart := plan.Command("force-restart", "Restart the plan with the provided name, or a specific phase in the plan with the provided name, or a specific step in a phase of the plan with the provided step name.").Alias("restart").Action(cmd.handleForceRestart)
	forceRestart.Arg("plan", "Name of the plan to restart").Required().StringVar(&cmd.planName)
	forceRestart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&cmd.phase)
	forceRestart.Arg("step", "Name or UUID of step to be restarted").StringVar(&cmd.step)

	forceComplete := plan.Command("force-complete", "Force complete a specific step in the provided phase. Example uses include the following: Abort a sidecar operation due to observed failure or known required manual preparation that was not performed").Alias("force").Action(cmd.handleForceComplete)
	forceComplete.Arg("plan", "Name of the plan to force complete").Required().StringVar(&cmd.planName)
	forceComplete.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&cmd.phase)
	forceComplete.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&cmd.step)
}
