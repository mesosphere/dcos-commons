package config

type tlsSetting int

const (
	TlsUnknown tlsSetting = iota
	TlsUnverified
	TlsVerified
	TlsSpecificCert
)
