package queries

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/config"
)

type describeRequest struct {
	AppID string `json:"appId"`
}

type updateRequest struct {
	AppID          string                 `json:"appId"`
	PackageVersion string                 `json:"packageVersion,omitempty"`
	OptionsJSON    map[string]interface{} `json:"options,omitempty"`
	Replace        bool                   `json:"replace"`
}

type Package struct {
}

func NewPackage() *Package {
	return &Package{}
}

func (q *Package) Describe() error {
	requestContent, err := json.Marshal(describeRequest{config.ServiceName})
	if err != nil {
		return err
	}
	responseBytes, err := client.HTTPCosmosPostJSON("describe", requestContent)
	if err != nil {
		return err
	}
	// This attempts to retrieve resolvedOptions from the response. This field is only provided by
	// Cosmos running on Enterprise DC/OS 1.10 clusters or later.
	resolvedOptionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "resolvedOptions")
	if err != nil {
		return fmt.Errorf("Failed to get 'resolvedOptions' field. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	if resolvedOptionsBytes != nil {
		client.PrintJSONBytes(resolvedOptionsBytes)
	} else {
		client.PrintMessage("Package configuration is not available for service %s.", config.ServiceName)
		client.PrintMessage("This command is only available for packages installed with Enterprise DC/OS 1.10 or newer.")
	}
	return nil
}

func (q *Package) VersionInfo() error {
	requestContent, _ := json.Marshal(describeRequest{config.ServiceName})
	responseBytes, err := client.HTTPCosmosPostJSON("describe", requestContent)
	if err != nil {
		return err
	}
	packageBytes, err := client.GetValueFromJSONResponse(responseBytes, "package")
	if err != nil {
		return fmt.Errorf("Failed to get 'package' field. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	currentVersionBytes, err := client.GetValueFromJSONResponse(packageBytes, "version")
	if err != nil {
		return fmt.Errorf("Failed to get 'package.version' field. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	downgradeVersionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "downgradesTo")
	if err != nil {
		return fmt.Errorf("Failed to get 'downgradesTo' field. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	downgradeVersions, err := client.JSONBytesToArray(downgradeVersionsBytes)
	if err != nil {
		return fmt.Errorf("Failed to convert 'downgradesTo' to array. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	upgradeVersionsBytes, err := client.GetValueFromJSONResponse(responseBytes, "upgradesTo")
	if err != nil {
		return fmt.Errorf("Failed to get 'upgradesTo' field. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	updateVersions, err := client.JSONBytesToArray(upgradeVersionsBytes)
	if err != nil {
		return fmt.Errorf("Failed to convert 'upgradesTo' to array. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}

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
	return nil
}

func (q *Package) Update(optionsFile, packageVersion string, replace bool) error {
	request := updateRequest{AppID: config.ServiceName, Replace: replace}
	if len(packageVersion) == 0 && len(optionsFile) == 0 {
		return fmt.Errorf("Either --options and/or --package-version must be specified")
	}
	if len(packageVersion) > 0 {
		request.PackageVersion = packageVersion
	}
	if len(optionsFile) > 0 {
		var fileBytes []byte
		var err error
		if optionsFile == "stdin" {
			// Read from stdin
			client.PrintMessage("Reading options from stdin...")
			fileBytes, err = ioutil.ReadAll(os.Stdin)
			if err != nil {
				return fmt.Errorf("Failed to read options from stdin: %s", err)
			}
		} else {
			// Read from file
			fileBytes, err = ioutil.ReadFile(optionsFile)
			if err != nil {
				return fmt.Errorf("Failed to read specified options file %s: %s", optionsFile, err)
			}
		}
		optionsJSON, err := client.UnmarshalJSON(fileBytes)
		if err != nil {
			return fmt.Errorf("Failed to parse JSON in provided options: %s\nContent (%d bytes): %s", err, len(fileBytes), string(fileBytes))
		}
		request.OptionsJSON = optionsJSON
	}
	requestContent, err := json.Marshal(request)
	if err != nil {
		return err
	}
	responseBytes, err := client.HTTPCosmosPostJSON("update", requestContent)
	if err != nil {
		return err
	}
	_, err = client.UnmarshalJSON(responseBytes)
	if err != nil {
		return fmt.Errorf("Failed to unmarshal response. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	client.PrintMessage(fmt.Sprintf("Update started. Please use `dcos %s --name=%s update status` to view progress.", config.ModuleName, config.ServiceName))
	return nil
}
