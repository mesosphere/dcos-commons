package config

type tlsSetting int

const (
	// TLSUnknown describes a connection where we do not currently know whether TLS is required.
	TLSUnknown tlsSetting = iota

	// TLSUnverified describes a connection where we do not need to verify the certificate.
	TLSUnverified

	// TLSVerified describes a connection where we should verify the certificate.
	TLSVerified

	// TLSSpecificCert describes a connection where a specific certificate has been provided.
	TLSSpecificCert
)
