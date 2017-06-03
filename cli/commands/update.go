package commands

import (
	"encoding/json"
	"fmt"
	"io/ioutil"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
	"gopkg.in/alecthomas/kingpin.v2"
)

type DescribeHandler struct {
	DescribeName string
}

type DescribeRequest struct {
	AppID string `json:"appId"`
}

func reportErrorAndExit(err error, responseBytes []byte) {
	client.LogMessage("Failed to unmarshal response. Error: %s", err)
	client.LogMessage("Original data follows:")
	client.LogMessageAndExit(string(responseBytes))
}

func doDescribe() {
	requestContent, _ := json.Marshal(DescribeRequest{config.ServiceName})
	response := client.HTTPCosmosPostJSON("describe", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	// This attempts to retrieve resolvedOptions from the response. This field is only provided by
	// Cosmos running on Enterprise DC/OS 1.10 clusters or later.
	resolvedOptionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "resolvedOptions")
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	if resolvedOptionsBytes != nil {
		client.PrintJSONBytes(resolvedOptionsBytes, nil)
	} else {
		client.LogMessage("No user options stored for service %s.", config.ServiceName)
		client.LogMessageAndExit("User options are only persisted for packages installed with Enterprise DC/OS 1.10 or newer.")
	}
}

func (cmd *DescribeHandler) DescribeConfiguration(c *kingpin.ParseContext) error {
	doDescribe()
	return nil
}

type UpdateHandler struct {
	UpdateName          string
	OptionsFile         string
	PackageVersion      string
	ViewPackageVersions bool
	ViewStatus          bool
}

type UpdateRequest struct {
	AppID          string                 `json:"appId"`
	PackageVersion string                 `json:"packageVersion,omitempty"`
	OptionsJSON    map[string]interface{} `json:"options,omitempty"`
}

func printPackageVersions() {
	requestContent, _ := json.Marshal(DescribeRequest{config.ServiceName})
	response := client.HTTPCosmosPostJSON("describe", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	packageBytes, err := client.GetValueFromJSONResponse(responseBytes, "package")
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	currentVersionBytes, err := client.GetValueFromJSONResponse(packageBytes, "version")
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	downgradeVersionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "downgradesTo")
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	upgradeVersionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "upgradesTo")
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	client.LogMessage("Current package version is: %s", currentVersionBytes)
	if downgradeVersionsBytes != nil {
		client.LogMessage("Valid package downgrade versions: %s", downgradeVersionsBytes)
	}
	if upgradeVersionsBytes != nil {
		client.LogMessage("Valid package upgrade versions: %s", upgradeVersionsBytes)
	}

}

func printStatus() {
	// TODO: implement
	client.LogMessage("Status has not been implemented yet. Please use `dcos %s --name=%s plan show` to view progress.", config.ModuleName, config.ServiceName)
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

func doUpdate(optionsFile, packageVersion string) {
	request := UpdateRequest{AppID: config.ServiceName}
	if len(packageVersion) > 0 {
		request.PackageVersion = packageVersion
	}
	if len(optionsFile) > 0 {
		optionsJSON, err := readJSONFile(optionsFile)
		if err != nil {
			client.LogMessage("Failed to load specified options file %s: %s", optionsFile, err)
			return
		}
		request.OptionsJSON = optionsJSON
	}
	requestContent, _ := json.Marshal(request)
	response := client.HTTPCosmosPostJSON("update", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	// TODO: do something interesting with update response
	_, err := parseUpdateResponse(responseBytes)
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	client.LogMessage(fmt.Sprintf("Update started. Please use `dcos %s --name=%s service update --status` to view progress.", config.ModuleName, config.ServiceName))
}

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
	if cmd.ViewPackageVersions {
		printPackageVersions()
		return nil
	}
	if cmd.ViewStatus {
		printStatus()
		return nil
	}
	doUpdate(cmd.OptionsFile, cmd.PackageVersion)
	return nil
}

func HandleDescribe(app *kingpin.Application) {
	describeCmd := &DescribeHandler{}
	app.Command("describe", "View the package configuration for this DC/OS service").Action(describeCmd.DescribeConfiguration)
}

func HandleUpdate(app *kingpin.Application) {
	updateCmd := &UpdateHandler{}
	update := app.Command("update", "Update the package version or configuration for this DC/OS service").Action(updateCmd.UpdateConfiguration)
	update.Flag("options", "Path to a JSON file that contains customized package installation options").StringVar(&updateCmd.OptionsFile)
	update.Flag("package-version", "The desired package version to update to").StringVar(&updateCmd.PackageVersion)
	update.Flag("package-versions", "View a list of available package versions to downgrade or upgrade to").BoolVar(&updateCmd.ViewPackageVersions)
	update.Flag("status", "View status of this update").BoolVar(&updateCmd.ViewStatus)
}
