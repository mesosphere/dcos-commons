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

func reportErrorAndExit(err error, responseBytes []byte) {
	log.Printf("Failed to unmarshal response. Error: %s", err)
	log.Printf("Original data follows:")
	outBuf := *bytes.NewBuffer(responseBytes)
	outBuf.WriteTo(os.Stdout)
	os.Exit(1)
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

func (cmd *DescribeHandler) DescribeConfiguration(c *kingpin.ParseContext) error {
	// TODO: add error handling
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
		log.Printf("No user options stored for service %s.", config.ServiceName)
		log.Fatalf("User options are only persisted for packages installed with Enterprise DC/OS 1.10 or newer.")
	}
	return nil
}

type UpdateHandler struct {
	UpdateName     string
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
	log.Printf("Status has not been implemented yet. Please use `dcos %s --name=%s plan show` to view progress.", config.ModuleName, config.ServiceName)
}

func checkAndReadJSONFile(filename string) (map[string]interface{}, error) {
	// TODO: any validation?
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

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
	if cmd.Status {
		printStatus()
		return nil
	}
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
	// TODO: do something interesting with update response
	_, err := parseUpdateResponse(responseBytes)
	if err != nil {
		reportErrorAndExit(err, responseBytes)
	}
	client.PrintText(fmt.Sprintf("Updated started. Please use `dcos %s --name=%s update --status` to view progress.", config.ModuleName, config.ServiceName))
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
	update.Flag("status", "View status of this update.").BoolVar(&updateCmd.Status)
}
