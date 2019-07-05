package main

import (
	"log"
	"os"
)

func main() {
	// Allow overriding of kadmin client
	kadminBin := os.Getenv("KADMIN_BIN")
	if kadminBin == "" {
		kadminBin = "/usr/sbin/kadmin"
	}

	// Create a KAdmin client
	kadmin, err := createKAdminClient(kadminBin)
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
