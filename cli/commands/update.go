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
}

type DescribeRequest struct {
	AppID string `json:"appId"`
}

func reportErrorAndExit(err error, responseBytes []byte) {
	client.LogMessage("Failed to unmarshal response. Error: %s", err)
	client.LogMessage("Original data follows:")
	client.LogMessageAndExit(string(responseBytes))
}

func parseDescribeResponse(responseBytes []byte) ([]byte, error) {
	// This attempts to retrieve resolvedOptions from the response. This field is only provided by
	// Cosmos running on Enterprise DC/OS 1.10 clusters or later.
	responseJSONBytes, err := client.UnmarshalJSON(responseBytes)
	if err != nil {
		return nil, err
	}
	if resolvedOptions, present := responseJSONBytes["resolvedOptions"]; present {
		resolvedOptionsBytes, err := json.Marshal(resolvedOptions)
		if err != nil {
			return nil, err
		}
		return resolvedOptionsBytes, nil
	}
	return nil, nil
}

func doDescribe() {
	requestContent, _ := json.Marshal(DescribeRequest{config.ServiceName})
	response := client.HTTPCosmosPostJSON("describe", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	resolvedOptionsBytes, err := parseDescribeResponse(responseBytes)
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	if resolvedOptionsBytes != nil {
		client.PrintJSONBytes(resolvedOptionsBytes, nil)
	} else {
		client.LogMessage("Package configuration is not available for service %s.", config.ServiceName)
		client.LogMessageAndExit("Only packages installed with Enterprise DC/OS 1.10 or newer will have configuration persisted.")
	}
}

func (cmd *DescribeHandler) DescribeConfiguration(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	doDescribe()
	return nil
}

func HandleDescribe(app *kingpin.Application) {
	describeCmd := &DescribeHandler{}
	app.Command("describe", "View the package configuration for this DC/OS service").Action(describeCmd.DescribeConfiguration)
}

type UpdateHandler struct {
	OptionsFile    string
	PackageVersion string
	Status         bool
}

type UpdateRequest struct {
	AppID          string                 `json:"appId"`
	PackageVersion string                 `json:"packageVersion,omitempty"`
	OptionsJSON    map[string]interface{} `json:"options,omitempty"`
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
	client.LogMessage(fmt.Sprintf("Update started. Please use `dcos %s --name=%s update --status` to view progress.", config.ModuleName, config.ServiceName))
}

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
	config.Command = c.SelectedCommand.FullCommand()
	if cmd.Status {
		printStatus()
		return nil
	}
	doUpdate(cmd.OptionsFile, cmd.PackageVersion)
	return nil
}

func HandleUpdate(app *kingpin.Application) {
	updateCmd := &UpdateHandler{}
	update := app.Command("update", "Update the package version or configuration for this DC/OS service").Action(updateCmd.UpdateConfiguration)
	update.Flag("options", "Path to a JSON file that contains customized package installation options").StringVar(&updateCmd.OptionsFile)
	update.Flag("package-version", "The desired package version").StringVar(&updateCmd.PackageVersion)
	update.Flag("status", "View status of this update").BoolVar(&updateCmd.Status)
}
