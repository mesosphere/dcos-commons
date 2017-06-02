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
		client.LogMessage("No user options stored for service %s.", config.ServiceName)
		client.LogMessageAndExit("User options are only persisted for packages installed with Enterprise DC/OS 1.10 or newer.")
	}
}

func (cmd *DescribeHandler) DescribeConfiguration(c *kingpin.ParseContext) error {
	doDescribe()
	return nil
}

type UpdateHandler struct {
	UpdateName     string
	OptionsFile    string
	PackageVersion string
	RawJson        bool
}

type UpdateRequest struct {
	AppID          string                 `json:"appId"`
	PackageVersion string                 `json:"packageVersion,omitempty"`
	OptionsJSON    map[string]interface{} `json:"options,omitempty"`
}

func doUpdate(optionsFile, packageVersion string) {
	request := UpdateRequest{AppID: config.ServiceName}
	if len(packageVersion) > 0 {
		request.PackageVersion = packageVersion
	}
	if len(optionsFile) > 0 {
		fileBytes, err := ioutil.ReadFile(optionsFile)
		if err != nil {
			client.LogMessageAndExit("Failed to load specified options file %s: %s", optionsFile, err)
			return
		}
		optionsJSON, err := client.UnmarshalJSON(fileBytes)
		if err != nil {
			client.LogMessageAndExit("Failed to parse JSON in specified options file %s: %s", optionsFile, err)
			return
		}
		request.OptionsJSON = optionsJSON
	}
	requestContent, _ := json.Marshal(request)
	response := client.HTTPCosmosPostJSON("update", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	_, err := client.UnmarshalJSON(responseBytes)
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	client.LogMessage(
		"Update started. Please use `dcos %s --name=%s service update status` to view progress.",
		config.ModuleName, config.ServiceName)
}

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
	doUpdate(cmd.OptionsFile, cmd.PackageVersion)
	return nil
}

func printStatus(rawJson bool) {
	planName := "deploy"
	response := client.HTTPServiceGet(fmt.Sprintf("v1/plans/%s", planName))
	if rawJson {
		client.PrintJSON(response)
	} else {
		client.LogMessage(toStatusTree(planName, client.GetResponseBytes(response)))
	}
}

func (cmd *UpdateHandler) PrintStatus(c *kingpin.ParseContext) error {
	printStatus(cmd.RawJson)
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
	update.Flag("package-version", "The desired package version").StringVar(&updateCmd.PackageVersion)

	status := update.Command("status", "View status of the current update").Alias("show").Action(updateCmd.PrintStatus)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&updateCmd.RawJson)
}
