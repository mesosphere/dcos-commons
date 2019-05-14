package main

import (
	"log"
	"os"
)

func main() {
	// Create a KAdmin client
	kadmin, err := CreateKAdminClient("/usr/sbin/kadmin")
	if err != nil {
		log.Fatal(err)
	}

	// Try to connect to DC/OS based on the environment settings
	// if this fails, we cannot connect to DC/OS later
	_, err = CreateDCOSClientFromEnvironment()
	if err != nil {
		log.Fatal(err)
	}

	// Create the KDC API server
	webPort := os.Getenv("PORT_WEB")
	if webPort == "" {
		webPort = "8080"
	}
	server := CreateKDCAPIServer(kadmin, webPort, "")
	server.Start()
}
