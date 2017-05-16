package cli

import (
	"errors"
	"fmt"
	"github.com/mesosphere/dcos-commons/cli/commands"
	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
	"os"
	"strings"
)

func GetModuleName() (string, error) {
	if len(os.Args) < 2 {
		return "", errors.New(fmt.Sprintf(
			"Must have at least one argument for the CLI module name: %s <modname>", os.Args[0]))
	}
	return os.Args[1], nil
}

func GetArguments() []string {
	// Exercise validation of argument count:
	if len(os.Args) < 2 {
		return make([]string, 0)
	}
	return os.Args[2:]
}

func HandleDefaultSections(app *kingpin.Application) {
	commands.HandleConfigSection(app)
	commands.HandleEndpointsSection(app)
	commands.HandlePlanSection(app)
	commands.HandlePodsSection(app)
	commands.HandleStateSection(app)
}

func New() *kingpin.Application {
	modName, err := GetModuleName()
	if err != nil {
		log.Fatalf(err.Error())
	}

	app := kingpin.New(fmt.Sprintf("dcos %s", modName), "")

	app.HelpFlag.Short('h') // in addition to default '--help'
	app.Flag("verbose", "Enable extra logging of requests/responses").Short('v').BoolVar(&config.Verbose)

	// This fulfills an interface that's expected by the main DC/OS CLI:
	// Prints a description of the module.
	app.Flag("info", "Show short description.").Hidden().PreAction(func(*kingpin.ParseContext) error {
		fmt.Fprintf(os.Stdout, "%s DC/OS CLI Module\n", strings.Title(modName))
		os.Exit(0)
		return nil
	}).Bool()

	app.Flag("force-insecure", "Allow unverified TLS certificates when querying service").BoolVar(&config.TlsForceInsecure)

	// Overrides of data that we fetch from DC/OS CLI:

	// Support using "DCOS_AUTH_TOKEN" or "AUTH_TOKEN" when available
	app.Flag("custom-auth-token", "Custom auth token to use when querying service").Envar("DCOS_AUTH_TOKEN").PlaceHolder("DCOS_AUTH_TOKEN").StringVar(&config.DcosAuthToken)
	// Support using "DCOS_URI" or "DCOS_URL" when available
	app.Flag("custom-dcos-url", "Custom cluster URL to use when querying service").Envar("DCOS_URI").Envar("DCOS_URL").PlaceHolder("DCOS_URI/DCOS_URL").StringVar(&config.DcosUrl)
	// Support using "DCOS_CA_PATH" or "DCOS_CERT_PATH" when available
	app.Flag("custom-cert-path", "Custom TLS CA certificate file to use when querying service").Envar("DCOS_CA_PATH").Envar("DCOS_CERT_PATH").PlaceHolder("DCOS_CA_PATH/DCOS_CERT_PATH").StringVar(&config.TlsCACertPath)

	// Default to --name <name> : use provided framework name (default to <modulename>.service_name, if available)
	serviceName := client.OptionalCLIConfigValue(fmt.Sprintf("%s.service_name", os.Args[1]))
	if len(serviceName) == 0 {
		serviceName = modName
	}
	app.Flag("name", "Name of the service instance to query").Default(serviceName).StringVar(&config.ServiceName)

	return app
}
