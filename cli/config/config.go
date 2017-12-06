package config

var (
	// DcosConfigName is the name of a configured DC/OS cluster config.
	// Used to manually specify an unattached config by name, otherwise the attached cluster config is used.
	DcosClusterName string
	// DcosConfigRootDir is the path to the root directory where configs are stored
	// Used to manually override the path to the config directory (default ~/.dcos).
	DcosConfigRootDir string
	// DcosConfigPath is the path to a DC/OS CLI .toml config.
	// Used to manually override the config path (default <DcosConfigRootDir>/dcos.toml).
	// Only takes effect if no suitable multicluster-style configs were found.
	DcosConfigPath string

	// ModuleName represents the name of this CLI module (e.g. "hello-world") and is read in from the $1 argument.
	ModuleName string
	// ServiceName represents the name of this instantiation of the service.
	// This defaults to the value of ModuleName unless overridden by the user.
	ServiceName string

	// Verbose will print additional messages to aid with debugging if set to true.
	Verbose bool
)
