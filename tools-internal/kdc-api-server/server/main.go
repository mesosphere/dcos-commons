package main

import (
	"log"
	"os"
)

func main() {

	// Allow overriding of kadmin cli
	kadminBin := os.Getenv("KADMIN_BIN")
	if kadminBin == "" {
		kadminBin = "kadmin"
	}

	// Allow overriding of ktutil cli
	ktutilBin := os.Getenv("KTUTIL_BIN")

	// Create a KAdmin client
	kadmin, err := createKAdminClient(kadminBin, ktutilBin)
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("Using %s-flavored kadmin from: %s", kadmin.KAdminApiFlavor, kadmin.KAdminPath)

	// Try to connect to DC/OS based on the environment settings
	// if this fails, we cannot connect to DC/OS later
	_, err = createDCOSClientFromEnvironment()
	if err != nil {
		log.Fatal(err)
	}

	// Create the KDC API server
	webPort := os.Getenv("PORT_WEB")
	if webPort == "" {
		webPort = "8080"
	}
	server := createKDCAPIServer(kadmin, webPort, "")
	server.Start()
}
