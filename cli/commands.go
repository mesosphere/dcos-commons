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
	commands.HandleConfigSection(app)
	commands.HandleDebugSection(app)
	commands.HandleDescribeSection(app)
	commands.HandleEndpointsSection(app)
	commands.HandlePlanSection(app)
	commands.HandlePodSection(app)
	commands.HandleStateSection(app)
	commands.HandleUpdateSection(app)
}

// New instantiates a new kingpin.Application and returns a reference to it.
// This contains basic flags that are universally applicable, e.g. --name.
func New() *kingpin.Application {
	modName, err := GetModuleName()
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	config.ModuleName = modName
	app := kingpin.New(fmt.Sprintf("dcos %s", config.ModuleName), "")

	app.GetFlag("help").Short('h') // in addition to default '--help'
	app.Flag("verbose", "Enable extra logging of requests/responses").Short('v').BoolVar(&config.Verbose)

	// This fulfills an interface that's expected by the main DC/OS CLI:
	// Prints a description of the module.
	app.Flag("info", "Show short description.").Hidden().PreAction(func(*kingpin.Application, *kingpin.ParseElement, *kingpin.ParseContext) error {
		fmt.Fprintf(os.Stdout, "%s DC/OS CLI Module\n", strings.Title(config.ModuleName))
		os.Exit(0)
		return nil
	}).Bool()

	app.Flag("force-insecure", "Allow unverified TLS certificates when querying service").BoolVar(&config.TLSForceInsecure)

	// Overrides of data that we fetch from DC/OS CLI:

	// Support using "DCOS_AUTH_TOKEN" or "AUTH_TOKEN" when available
	app.Flag("custom-auth-token", "Custom auth token to use when querying service").Envar("DCOS_AUTH_TOKEN").PlaceHolder("DCOS_AUTH_TOKEN").StringVar(&config.DcosAuthToken)
	// Support using "DCOS_URI" or "DCOS_URL" when available
	app.Flag("custom-dcos-url", "Custom cluster URL to use when querying service").Envar("DCOS_URI").Envar("DCOS_URL").PlaceHolder("DCOS_URI/DCOS_URL").StringVar(&config.DcosURL)
	// Support using "DCOS_CA_PATH" or "DCOS_CERT_PATH" when available
	app.Flag("custom-cert-path", "Custom TLS CA certificate file to use when querying service").Envar("DCOS_CA_PATH").Envar("DCOS_CERT_PATH").PlaceHolder("DCOS_CA_PATH/DCOS_CERT_PATH").StringVar(&config.TLSCACertPath)

	// Default to --name <name> : use provided framework name (default to <modulename>.service_name, if available)
	serviceName := client.OptionalCLIConfigValue(fmt.Sprintf("%s.service_name", os.Args[1]))
	if len(serviceName) == 0 {
		serviceName = config.ModuleName
	}
	app.Flag("name", "Name of the service instance to query").Default(serviceName).StringVar(&config.ServiceName)

	return app
}
