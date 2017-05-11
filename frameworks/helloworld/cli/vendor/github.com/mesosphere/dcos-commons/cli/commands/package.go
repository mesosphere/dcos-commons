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
    fmt.Printf("Describin")
    return nil
}

type UpdateHandler struct{
    UpdateName string
    File *os.File
    PackageVersion string
}

func (cmd *UpdateHandler) UpdateConfiguration(c *kingpin.ParseContext) error {
    fmt.Printf("Updatin")
    return nil
}

func HandlePackageSection(app *kingpin.Application) {
    pkg := app.Command("package", "Manage service package configuration")

    describeCmd := &DescribeHandler{}
    pkg.Command("describe", "View the package configuration for this DC/OS service").Action(describeCmd.DescribeConfiguration)

    updateCmd := &UpdateHandler{}
    update := pkg.Command("update", "Update the package version or configuration for this DC/OS service").Action(updateCmd.UpdateConfiguration)
    update.Flag("--options", "Path to a JSON file that contains customized package installation options").FileVar(&updateCmd.File)
    update.Flag("--package-version", "The desired package version.").StringVar(&updateCmd.PackageVersion)
}