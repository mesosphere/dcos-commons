/*
Package cli is the entrypoint to writing a custom CLI for a service built using the DC/OS SDK.
It provides a number of standard subcommands that allow users to interact with their service.
*/
package cli

import (
	"fmt"
	"os"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/client"
	"github.com/mesosphere/dcos-commons/cli/commands"
	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/mesosphere/dcos-commons/cli/queries"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

// GetModuleName returns the module name, if it was passed in, or an error otherwise.
func GetModuleName() (string, error) {
	if len(os.Args) < 2 {
		return "", fmt.Errorf(
			"Must have at least one argument for the CLI module name: %s <modname>", os.Args[0])
	}
	return os.Args[1], nil
}

// GetArguments returns an array of the arguments passed into this CLI.
func GetArguments() []string {
	// Exercise validation of argument count:
	if len(os.Args) < 2 {
		return make([]string, 0)
	}
	return os.Args[2:]
}

// HandleDefaultSections is a utility method to allow applications built around this library to provide
// all of the standard subcommands of the CLI.
func HandleDefaultSections(app *kingpin.Application) {
	podQueries := queries.NewPod()
	packageQueries := queries.NewPackage()
	planQueries := queries.NewPlan()

	commands.HandleDebugSection(app, queries.NewConfig(), podQueries, queries.NewState())
	commands.HandleDeprecatedConfigSection(app)
	commands.HandleDescribeSection(app, packageQueries)
	commands.HandleEndpointsSection(app, queries.NewEndpoints())
	commands.HandlePlanSection(app, planQueries)
	commands.HandlePodSection(app, podQueries)
	commands.HandleDeprecatedStateSection(app)
	commands.HandleUpdateSection(app, packageQueries, planQueries)
}

// New instantiates a new kingpin.Application and returns a reference to it.
// This contains basic flags that are universally applicable, e.g. --name.
func New() *kingpin.Application {
	// Before doing anything else, check for envvars relating to logging, and update config.Verbose to reflect them.
	// We do this outside of the normal flag handling for two reasons:
	// - We want the Verbose bit to be set as early as possible, even before arg handling starts.
	// - In kingpin, setting an envvar just changes a default value and doesn't trigger any actions.
	if strings.EqualFold(os.Getenv("DCOS_DEBUG"), "true") {
		config.Verbose = true
	} else {
		logLevel := os.Getenv("DCOS_LOG_LEVEL")
		// Treat either "info" or "debug" as verbose:
		if strings.EqualFold(logLevel, "info") || strings.EqualFold(logLevel, "debug") {
			config.Verbose = true
		}
	}

	var err error
	config.ModuleName, err = GetModuleName()
	if err != nil {
		client.PrintMessage(err.Error())
		os.Exit(1)
	}
	app := kingpin.New(fmt.Sprintf("dcos %s", config.ModuleName), "")

	app.GetFlag("help").Short('h') // in addition to default '--help'

	// Enable verbose logging with '-v', in addition to DCOS_DEBUG/DCOS_LOG_LEVEL which are handled above.
	app.Flag("verbose", "Enable extra logging of requests/responses").Short('v').BoolVar(&config.Verbose)

	// --info and --config-schema are required by the main DC/OS CLI:
	// Prints a description of the module.
	app.Flag("info", "Show short description.").Hidden().PreAction(func(*kingpin.Application, *kingpin.ParseElement, *kingpin.ParseContext) error {
		fmt.Fprintf(os.Stdout, "%s DC/OS CLI Module\n", strings.Title(config.ModuleName))
		os.Exit(0)
		return nil
	}).Bool()
	// Prints a description of the module config schema (only a 'service_name' option).
	app.Flag("config-schema", "Show config schema.").Hidden().PreAction(func(*kingpin.Application, *kingpin.ParseElement, *kingpin.ParseContext) error {
		fmt.Fprint(os.Stdout, `{
  "$schema": "http://json-schema.org/schema#",
  "additionalProperties": false,
  "properties": { "service_name": { "title": "Service name", "type": "string" } },
  "type": "object"
}
`)
		os.Exit(0)
		return nil
	}).Bool()

	// Support envvars documented by the main DC/OS CLI to select the correct cluster config.
	// These flags are hidden from help output as the main interface is the envvars.
	// See also: https://dcos.io/docs/1.10/cli/#environment-variables

	// Cluster name override: specify name of added but unattached cluster config
	app.Flag("custom-cluster-name", "Name of a configured cluster, otherwise the attached cluster is used").Hidden().Envar("DCOS_CLUSTER").PlaceHolder("DCOS_CLUSTER").StringVar(&config.DcosClusterName)
	// Config root dir override: direct path to .dcos/ contents
	app.Flag("custom-config-dir", "Path to DC/OS config directory").Hidden().Envar("DCOS_DIR").PlaceHolder("DCOS_DIR").StringVar(&config.DcosConfigRootDir)
	// Config file override: direct path to a .toml file. Only takes effect if no 0.5.x-style cluster configs are found.
	app.Flag("custom-cluster-config", "Path to DC/OS config .toml file, if no clusters are configured").Hidden().Envar("DCOS_CONFIG").PlaceHolder("DCOS_CONFIG").StringVar(&config.DcosConfigPath)

	// Default to --name <name> : use provided framework name (default to <modulename>.service_name, if available)
	serviceName := client.OptionalCLIConfigValue(fmt.Sprintf("%s.service_name", config.ModuleName))
	if len(serviceName) == 0 {
		// No <modulename>.service_name setting was found.
		// Guess the default service name based off the module/package name itself.
		// In particular, if the module/package is named 'beta-foo', then use a default of 'foo'.
		// This aligns with the convention for service names used by 'beta-' packages.
		serviceName = strings.TrimPrefix(config.ModuleName, "beta-")
	}
	app.Flag("name", "Name of the service instance to query").Default(serviceName).StringVar(&config.ServiceName)

	return app
}
