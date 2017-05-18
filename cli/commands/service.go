package commands

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"os"

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

func unmarshalJSON(jsonBytes []byte) (map[string]interface{}, error) {
	var responseJSON map[string]interface{}
	err := json.Unmarshal([]byte(jsonBytes), &responseJSON)
	if err != nil {
		return nil, err
	}
	return responseJSON, nil
}

func reportErrorAndExit(err error, responseBytes []byte) {
	log.Printf("Failed to unmarshal response. Error: %s", err)
	log.Printf("Original data follows:")
	outBuf := *bytes.NewBuffer(responseBytes)
	outBuf.WriteTo(os.Stdout)
	os.Exit(1)
}

func parseDescribeResponse(responseBytes []byte) ([]byte, error) {
	// TODO: what is the intended output here?
	// Do we want to show upgradesTo/downgradesTo components? resolvedOptions or userProvidedOptions?
	// TODO: is there a better way to do this instead of unmarshalling and remarshalling?
	// TODO: add some error handling here in case the format changes
	responseJSONBytes, err := unmarshalJSON(responseBytes)
	if err != nil {
		return nil, err
	}
	resolvedOptions, err := json.Marshal(responseJSONBytes["resolvedOptions"])
	if err != nil {
		return nil, err
	}
	return resolvedOptions, nil
}

func (cmd *DescribeHandler) DescribeConfiguration(c *kingpin.ParseContext) error {
	// TODO: add error handling
	requestContent, _ := json.Marshal(DescribeRequest{config.ServiceName})
	response := client.HTTPCosmosPostJSON("describe", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	resolvedOptions, err := parseDescribeResponse(responseBytes)
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	client.PrintJSONBytes(resolvedOptions, nil)
	return nil
}

type UpdateHandler struct {
	UpdateName     string
	OptionsFile    string
	PackageVersion string
}

type UpdateRequest struct {
	AppID          string                 `json:"appId"`
	PackageVersion string                 `json:"packageVersion,omitempty"`
	OptionsJSON    map[string]interface{} `json:"options,omitempty"`
}

func checkAndReadJSONFile(filename string) (map[string]interface{}, error) {
	// TODO: any validation?
	fileBytes, err := ioutil.ReadFile(filename)
	if err != nil {
		return nil, err
	}
	return unmarshalJSON(fileBytes)
}

func parseUpdateResponse(responseBytes []byte) (string, error) {
	// TODO: do something interesting with this output
	// Output should be in the same format as the `dcos marathon app update` command
	responseJSON, err := unmarshalJSON(responseBytes)
	if err != nil {
		return "", err
	}
	return string(responseJSON["marathonDeploymentId"].(string)), nil
}

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
	// TODO: add error handling
	request := UpdateRequest{AppID: config.ServiceName}
	if len(cmd.PackageVersion) > 0 {
		// TODO: check package version format is valid
		request.PackageVersion = cmd.PackageVersion
	}
	if len(cmd.OptionsFile) > 0 {
		optionsJSON, err := checkAndReadJSONFile(cmd.OptionsFile)
		if err != nil {
			log.Fatalf("Failed to load specified options file %s: %s", cmd.OptionsFile, err)
		}
		request.OptionsJSON = optionsJSON
	}
	requestContent, _ := json.Marshal(request)
	response := client.HTTPCosmosPostJSON("update", string(requestContent))
	responseBytes := client.GetResponseBytes(response)
	deploymentID, err := parseUpdateResponse(responseBytes)
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	client.PrintText(fmt.Sprintf("Created deployment %s", deploymentID))
	return nil
}

func HandleServiceSection(app *kingpin.Application) {
	pkg := app.Command("service", "Manage service package configuration")

	describeCmd := &DescribeHandler{}
	pkg.Command("describe", "View the package configuration for this DC/OS service").Action(describeCmd.DescribeConfiguration)

	updateCmd := &UpdateHandler{}
	update := pkg.Command("update", "Update the package version or configuration for this DC/OS service").Action(updateCmd.UpdateConfiguration)
	update.Flag("options", "Path to a JSON file that contains customized package installation options").StringVar(&updateCmd.OptionsFile)
	update.Flag("package-version", "The desired package version.").StringVar(&updateCmd.PackageVersion)
}
