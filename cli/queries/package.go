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

type describePackageResponse struct {
	Version string `json:"version"`
}

type describeResponse struct {
	Package         describePackageResponse `json:"package"`
	UpgradesTo      []string                `json:"upgradesTo"`
	DowngradesTo    []string                `json:"downgradesTo"`
	// Note: ResolvedOptions is only provided on DC/OS EE 1.10 or later
	ResolvedOptions map[string]interface{}  `json:"resolvedOptions"`
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
	response := describeResponse{}
	err = json.Unmarshal(responseBytes, &response)
	if err != nil {
		return fmt.Errorf("Failed to decode 'describe' response. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	// This attempts to retrieve resolvedOptions from the response. This field is only provided by
	// Cosmos running on Enterprise DC/OS 1.10 clusters or later.
	if len(response.ResolvedOptions) > 0 {
		optionsStr, err := json.MarshalIndent(response.ResolvedOptions, "", "  ")
		if err != nil {
			return fmt.Errorf("Failed to encode resolved options. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
		}
		client.PrintMessage("%s", optionsStr)
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

	response := describeResponse{}
	err = json.Unmarshal(responseBytes, &response)
	if err != nil {
		return fmt.Errorf("Failed to decode 'describe' response. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}

	client.PrintMessage("Current package version is: %s", response.Package.Version)

	if len(response.DowngradesTo) > 0 {
		client.PrintMessage("Package can be downgraded to:\n%s", client.FormatList(response.DowngradesTo))
	} else {
		client.PrintMessage("No valid package downgrade versions.")
	}

	if len(response.UpgradesTo) > 0 {
		client.PrintMessage("Package can be upgraded to:\n%s", client.FormatList(response.UpgradesTo))
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
		var optionsJSON map[string]interface{}
		err = json.Unmarshal(fileBytes, &optionsJSON)
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
	// To determine if the request was successful, just try to trivially decode the response as JSON
	var response map[string]interface{}
	err = json.Unmarshal(responseBytes, &response)
	if err != nil {
		return fmt.Errorf("Failed to unmarshal response. Error: %s\nOriginal data:\n%s", err, string(responseBytes))
	}
	client.PrintMessage(fmt.Sprintf("Update started. Please use `dcos %s --name=%s update status` to view progress.", config.ModuleName, config.ServiceName))
	return nil
}
