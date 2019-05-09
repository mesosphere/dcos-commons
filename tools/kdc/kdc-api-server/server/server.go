package main

import (
	"fmt"
	"net/http"
)

type KDCAPIServer struct {
	kadmin   *KAdminClient
	endpoint string
}

func CreateKDCAPIServer(kadmin *KAdminClient, port string, host string) *KDCAPIServer {
	inst := &KDCAPIServer{
		kadmin:   kadmin,
		endpoint: fmt.Sprintf("%s:%s", host, port),
	}

	// Start static file serving
	fs := http.FileServer(http.Dir("static/"))
	http.Handle("/static/", http.StripPrefix("/static/", fs))
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "Welcome to KDC Admin")
	})

	// Start API endpoint serving
	http.HandleFunc("/api/add-principal", inst.handleAddPrincipal)

	return inst
}

func (s *KDCAPIServer) Start() {
	http.ListenAndServe(s.endpoint, nil)
}

func (s *KDCAPIServer) handleAddPrincipal(rw http.ResponseWriter, req *http.Request) {
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

	// Since our auth token can expire at any time, we are re-connecting on
	// DC/OS on every request. Since we are not expecting any serious request
	// rate on this endpoint,  and since the log-in procedure is quite fast
	// we should be OK
	dclient, err := CreateDCOSClientFromEnvironment()
	if err != nil {
		fmt.Fprintf(rw, "error: Unable to connect to DC/OS: %s", err.Error())
		return
	}

	// Install missing principals using defaults
	err = s.kadmin.AddMissingPrincipals(principals)
	if err != nil {
		fmt.Fprintf(rw, "error: Unable to add principals: %s", err.Error())
		return
	}

	// Collect keytab contents
	keytab, err := s.kadmin.GetKeytabForPrincipals(principals)
	if err != nil {
		fmt.Fprintf(rw, "error: Unable to export keytab: %s", err.Error())
		return
	}

	// Upload to DC/OS secret store
	err = CreateKeytabSecret(dclient, serviceName[0], keytab)
	if err != nil {
		fmt.Fprintf(rw, "error: Unable to upload to secret store: %s", err.Error())
		return
	}

	// We are done
	fmt.Fprintf(rw, "ok")
}
