package config

var (
	DcosAuthToken string
	DcosUrl       string
	ServiceName   string

	TlsForceInsecure bool
	TlsCliSetting    tlsSetting = TlsUnknown
	TlsCACertPath    string

	Verbose bool
)
