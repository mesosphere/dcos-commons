package config

var (
	// DcosAuthToken used to authenticate against DC/OS. Read from the DC/OS CLI, if set.
	DcosAuthToken string
	// DcosURL is the DC/OS master's URL. Read from the DC/OS CLI, if set.
	DcosURL string
	// CosmosURL can point to a local Cosmos instance for testing. Read from the DC/OS CLI, if set.
	CosmosURL string
	// ModuleName represents the name of this CLI module (e.g. "hello-world") and is read in from the $1 argument.
	ModuleName string
	// ServiceName represents the name of this instantiation of the service. If unspecified by the user, this defaults to
	// the same as ModuleName.
	ServiceName string
	// Command represents the name of the specific subcommand and is used to provide more helpful error messages.
	Command string

	// TLSForceInsecure forces insecure connections if set.
	TLSForceInsecure bool
	// TLSCliSetting represents whether or not certificates should be verified or not.
	TLSCliSetting = TLSUnknown
	// TLSCACertPath represents the path to a certificate to use when speaking to a DC/OS cluster.
	TLSCACertPath string

	// Verbose will print additional messages to aid with debugging if set to true.
	Verbose bool
)
