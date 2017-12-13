package commands

import (
	"encoding/json"
	"fmt"
	"io/ioutil"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type describeHandler struct{}

type describeRequest struct {
	AppID string `json:"appId"`
}

func checkError(err error, responseBytes []byte) {
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
}

func reportErrorAndExit(err error, responseBytes []byte) {
	client.PrintMessage("Failed to unmarshal response. Error: %s", err)
	client.PrintMessage("Original data follows:")
	client.PrintMessageAndExit(string(responseBytes))
}

func describe() {
	requestContent, err := json.Marshal(describeRequest{config.ServiceName})
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	responseBytes, err := client.HTTPCosmosPostJSON("describe", string(requestContent))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	// This attempts to retrieve resolvedOptions from the response. This field is only provided by
	// Cosmos running on Enterprise DC/OS 1.10 clusters or later.
	resolvedOptionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "resolvedOptions")
	checkError(err, responseBytes)
	if resolvedOptionsBytes != nil {
		client.PrintJSONBytes(resolvedOptionsBytes)
	} else {
		client.PrintMessage("Package configuration is not available for service %s.", config.ServiceName)
		client.PrintMessage("This command is only available for packages installed with Enterprise DC/OS 1.10 or newer.")
	}
}

func (cmd *describeHandler) handleDescribe(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	describe()
	return nil
}

// HandleDescribeSection adds the describe subcommand to the passed in kingpin.Application.
func HandleDescribeSection(app *kingpin.Application) {
	cmd := &describeHandler{}
	app.Command("describe", "View the configuration for this service").Action(cmd.handleDescribe)
}

type updateHandler struct {
	UpdateName     string
	OptionsFile    string
	PackageVersion string
	ViewStatus     bool
	Replace        bool
}

type updateRequest struct {
	AppID          string                 `json:"appId"`
	PackageVersion string                 `json:"packageVersion,omitempty"`
	OptionsJSON    map[string]interface{} `json:"options,omitempty"`
	Replace        bool                   `json:"replace"`
}

func printPackageVersions() {
	requestContent, _ := json.Marshal(describeRequest{config.ServiceName})
	responseBytes, err := client.HTTPCosmosPostJSON("describe", string(requestContent))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	packageBytes, err := client.GetValueFromJSONResponse(responseBytes, "package")
	checkError(err, responseBytes)
	currentVersionBytes, err := client.GetValueFromJSONResponse(packageBytes, "version")
	checkError(err, responseBytes)
	downgradeVersionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "downgradesTo")
	checkError(err, responseBytes)
	downgradeVersions, err := client.JSONBytesToArray(downgradeVersionsBytes)
	checkError(err, responseBytes)
	upgradeVersionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "upgradesTo")
	checkError(err, responseBytes)
	updateVersions, err := client.JSONBytesToArray(upgradeVersionsBytes)
	checkError(err, responseBytes)
	client.PrintMessage("Current package version is: %s", currentVersionBytes)
	if len(downgradeVersions) > 0 {
		client.PrintMessage("Package can be downgraded to: %s", client.PrettyPrintSlice(downgradeVersions))
	} else {
		client.PrintMessage("No valid package downgrade versions.")
	}
	if len(updateVersions) > 0 {
		client.PrintMessage("Package can be upgraded to: %s", client.PrettyPrintSlice(updateVersions))
	} else {
		client.PrintMessage("No valid package upgrade versions.")
	}
}

func (cmd *updateHandler) ViewPackageVersions(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	printPackageVersions()
	return nil
}

func readJSONFile(filename string) (map[string]interface{}, error) {
	fileBytes, err := ioutil.ReadFile(filename)
	if err != nil {
		return nil, err
	}
	return client.UnmarshalJSON(fileBytes)
}

func parseUpdateResponse(responseBytes []byte) (string, error) {
	responseJSON, err := client.UnmarshalJSON(responseBytes)
	if err != nil {
		return "", err
	}
	return string(responseJSON["marathonDeploymentId"].(string)), nil
}

func doUpdate(optionsFile, packageVersion string, replace bool) {
	request := updateRequest{AppID: config.ServiceName, Replace: replace}
	if len(packageVersion) == 0 && len(optionsFile) == 0 {
		client.PrintMessage("Either --options and/or --package-version must be specified. See --help.")
		return
	}
	if len(packageVersion) > 0 {
		request.PackageVersion = packageVersion
	}
	if len(optionsFile) > 0 {
		fileBytes, err := ioutil.ReadFile(optionsFile)
		if err != nil {
			client.PrintMessageAndExit("Failed to load specified options file %s: %s", optionsFile, err)
			return
		}
		optionsJSON, err := client.UnmarshalJSON(fileBytes)
		if err != nil {
			client.PrintMessageAndExit("Failed to parse JSON in specified options file %s: %s\nContent (%d bytes): %s", optionsFile, err, len(fileBytes), string(fileBytes))
			return
		}
		request.OptionsJSON = optionsJSON
	}
	requestContent, _ := json.Marshal(request)
	responseBytes, err := client.HTTPCosmosPostJSON("update", string(requestContent))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
		return
	}
	_, err = client.UnmarshalJSON(responseBytes)
	checkError(err, responseBytes)
	client.PrintMessage(fmt.Sprintf("Update started. Please use `dcos %s --name=%s update status` to view progress.", config.ModuleName, config.ServiceName))
}

func (cmd *updateHandler) UpdateConfiguration(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	doUpdate(cmd.OptionsFile, cmd.PackageVersion, cmd.Replace)
	return nil
}

// HandleUpdateSection adds the update subcommand to the passed in kingpin.Application.
func HandleUpdateSection(app *kingpin.Application) {
	cmd := &updateHandler{}
	update := app.Command("update", "Updates the package version or configuration for this DC/OS service")

	start := update.Command("start", "Launches an update operation").Action(cmd.UpdateConfiguration)
	start.Flag("options", "Path to a JSON file that contains customized package installation options").StringVar(&cmd.OptionsFile)
	start.Flag("package-version", "The desired package version").StringVar(&cmd.PackageVersion)
	start.Flag("replace", "Replace the existing configuration in whole. Otherwise, the existing configuration and options are merged.").BoolVar(&cmd.Replace)

	planCmd := &planHandler{}

	forceComplete := update.Command("force-complete", "Force complete a specific step in the provided phase").Alias("force").Action(planCmd.handleForceComplete)
	forceComplete.Arg("phase", "Name or UUID of the phase containing the provided step").Required().StringVar(&planCmd.Phase)
	forceComplete.Arg("step", "Name or UUID of step to be restarted").Required().StringVar(&planCmd.Step)

	forceRestart := update.Command("force-restart", "Restart update plan, or specific step in the provided phase").Alias("restart").Action(planCmd.handleForceRestart)
	forceRestart.Arg("phase", "Name or UUID of the phase containing the provided step").StringVar(&planCmd.Phase)
	forceRestart.Arg("step", "Name or UUID of step to be restarted").StringVar(&planCmd.Step)

	update.Command("package-versions", "View a list of available package versions to downgrade or upgrade to").Action(cmd.ViewPackageVersions)

	update.Command("pause", "Pause update plan").Alias("interrupt").Action(planCmd.handlePause)

	update.Command("resume", "Resume update plan").Alias("continue").Action(planCmd.handleResume)

	status := update.Command("status", "View status of a running update").Alias("show").Action(planCmd.handleStatus)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&planCmd.RawJSON)
	// ensure plan name is passed
	status.Flag("plan", "Name of the plan to launch").Default("deploy").Hidden().StringVar(&planCmd.PlanName)
}
