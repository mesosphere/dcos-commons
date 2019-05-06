package main

import (
	"fmt"
	"log"
	"net/http"
)

func main() {

	// Create a KAdmin client
	kadmin, err := CreateKAdminClient("kadmin")
	if err != nil {
		log.Fatal(err)
	}

	// Try to connect to DC/OS based on the environment settings
	// if this fails, we cannot connect to DC/OS later
	dclient, err := CreateDCOSClientFromEnvironment()
	if err != nil {
		log.Fatal(err)
	}

	// Start the server
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "Welcome to my website!")
	})

	http.HandleFunc("/api/add-principal", func(rw http.ResponseWriter, req *http.Request) {

		// Check the name of the service
		serviceName, ok := req.URL.Query()["service"]
		if !ok || len(serviceName[0]) < 1 {
			fmt.Fprintf(rw, "error: missing 'service=' argument")
			return
		}

		// Make sure we have the required arguments
		principals, err := ParsePrincipalsFrom(req.Body)
		if err != nil {
			fmt.Fprintf(rw, "error: %s", err.Error())
			return
		}

		// Check if we were given an empty string
		if len(principals) == 0 {
			fmt.Fprintf(rw, "error: Given an empty list of principals")
			return
		}

		// Install missing principals using defaults
		err = kadmin.AddMissingPrincipals(principals)
		if err != nil {
			fmt.Fprintf(rw, "error: Unable to add principals: %s", err.Error())
			return
		}

		// Collect keytab contents
		keytab, err := kadmin.GetKeytabForPrincipals(principals)
		if err != nil {
			fmt.Fprintf(rw, "error: Unable to export keytab: %s", err.Error())
			return
		}

		// Upload to
		err = CreateKeytabSecret(dclient, serviceName[0], keytab)
		if err != nil {
			fmt.Fprintf(rw, "error: Unable to upload to secret store: %s", err.Error())
			return
		}

		fmt.Fprintf(rw, "ok")
	})

	fs := http.FileServer(http.Dir("static/"))
	http.Handle("/static/", http.StripPrefix("/static/", fs))

	log.Printf("Listening on port 8080\n")
	http.ListenAndServe(":8080", nil)
}
