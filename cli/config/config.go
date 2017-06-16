package config

var (
	DcosAuthToken string
	DcosUrl       string
	CosmosUrl     string
	ModuleName    string
	ServiceName   string
	Command       string

	TlsForceInsecure bool
	TlsCliSetting    tlsSetting = TlsUnknown
	TlsCACertPath    string

	Verbose bool
)
