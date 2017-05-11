package commands

import (
    "gopkg.in/alecthomas/kingpin.v2"
    "fmt"
    "os"
)

type DescribeHandler struct {
    DescribeName string
}

func (cmd *DescribeHandler) DescribeConfiguration(c *kingpin.ParseContext) error {
    // Call out to <dcos_url>/cosmos/service/describe w/ auth header
    //      Accept:'application/vnd.dcos.service.describe-response+json;charset=utf-8;version=v1' \
    //      Content-Type:'application/vnd.dcos.service.describe-request+json;charset=utf-8;version=v1' \
    //      appId="$1"
    fmt.Printf("Describin")
    return nil
}

type UpdateHandler struct{
    UpdateName string
    File *os.File
    PackageVersion string
}

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
    // Call out to <dcos_url>/cosmos/service/update w/ auth header
    // Accept:'application/vnd.dcos.service.update-response+json;charset=utf-8;version=v1' \
    // Content-Type:'application/vnd.dcos.service.update-request+json;charset=utf-8;version=v1' \
    fmt.Printf("Updatin")
    return nil
}

func HandleServiceSection(app *kingpin.Application) {
    pkg := app.Command("service", "Manage service package configuration")

    describeCmd := &DescribeHandler{}
    pkg.Command("describe", "View the package configuration for this DC/OS service").Action(describeCmd.DescribeConfiguration)

    updateCmd := &UpdateHandler{}
    update := pkg.Command("update", "Update the package version or configuration for this DC/OS service").Action(updateCmd.UpdateConfiguration)
    update.Flag("--options", "Path to a JSON file that contains customized package installation options").FileVar(&updateCmd.File)
    update.Flag("--package-version", "The desired package version.").StringVar(&updateCmd.PackageVersion)
}