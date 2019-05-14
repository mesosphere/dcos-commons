package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
)

type KDCAPIServer struct {
	kadmin   *KAdminClient
	endpoint string
}

type KDCRequestAddPrincipal struct {
	PrincipalsRaw string       `json:"principals_raw"`
	Principals    []KPrincipal `json:"principals"`
	Secret        string       `json:"secret"`
	Binary        *bool        `json:"binary"`
}

type KDCRequestListPrincipals struct {
	Filter string `json:"filter"`
}

type KDCResponse struct {
	Status string      `json:"status"`
	Error  string      `json:"error,omitempty"`
	Data   interface{} `json:"data,omitempty"`
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
	http.HandleFunc("/api/add", inst.handleAddPrincipal)
	http.HandleFunc("/api/list", inst.handleListPrincipals)

	return inst
}

func (s *KDCAPIServer) Start() {
	log.Printf("Listening on %s\n", s.endpoint)
	http.ListenAndServe(s.endpoint, nil)
}

/**
 * Respond with an error, formatting it according to the request type
 */
func (s *KDCAPIServer) replyReject(rw http.ResponseWriter, req *http.Request, format string, args ...interface{}) {
	contentType := req.Header.Get("Content-Type")
	if contentType == "application/json" {
		resp := KDCResponse{
			"error",
			fmt.Sprintf(format, args...),
			nil,
		}

		// Marshal the data
		js, err := json.Marshal(resp)
		if err != nil {
			http.Error(rw, err.Error(), http.StatusInternalServerError)
			return
		}

		// Respond with JSON-encoded text
		rw.Header().Set("Content-Type", "application/json")
		rw.Write(js)
	} else {
		// Otherwise respond with raw text
		rw.Header().Set("Content-Type", "text/plain")
		fmt.Fprintf(rw, "errr: "+format, args...)
	}
}

func (s *KDCAPIServer) replySuccess(rw http.ResponseWriter, req *http.Request, data interface{}) {
	contentType := req.Header.Get("Content-Type")
	if contentType == "application/json" {
		resp := KDCResponse{
			"ok", "", data,
		}

		// Marshal the data
		js, err := json.Marshal(resp)
		if err != nil {
			http.Error(rw, err.Error(), http.StatusInternalServerError)
			return
		}

		// Respond with JSON-encoded text
		rw.Header().Set("Content-Type", "application/json")
		rw.Write(js)
	} else {
		// Otherwise respond with raw text
		rw.Header().Set("Content-Type", "text/plain")
		if data != nil {
			switch x := data.(type) {
			case []KPrincipal:
				for _, v := range x {
					fmt.Fprintln(rw, v.String())
				}
				return
			}
		}

		fmt.Fprintf(rw, "ok")
	}
}

/**
 * Add one or more principals to the KDC server, and create the respective
 * service secret name
 */
func (s *KDCAPIServer) handleAddPrincipal(rw http.ResponseWriter, req *http.Request) {
	var apiReq KDCRequestAddPrincipal

	// We accept only POST
	if req.Method != "POST" {
		s.replyReject(rw, req, `Accepting only POST requests on this endpoint`)
		return
	}

	// Check if we are parsing JSON or plain text
	contentType := req.Header.Get("Content-Type")
	if contentType == "application/json" {

		// We are expeting all the interesting data to be in the payload
		dec := json.NewDecoder(req.Body)
		if err := dec.Decode(&apiReq); err == io.EOF {
			s.replyReject(rw, req, `Could not decode input`)
			return
		} else if err != nil {
			s.replyReject(rw, req, `Unable to parse request: %s`, err.Error())
			return
		}

		// If we were given a raw principals list, parse it now
		if apiReq.PrincipalsRaw != "" {
			principalsList, err := ParsePrincipalsFrom(strings.NewReader(apiReq.PrincipalsRaw))
			if err != nil {
				s.replyReject(rw, req, `unable to parse principals: %s`, err.Error())
				return
			}
			apiReq.Principals = principalsList
		}

	} else {

		// Otherwise we expect the secret name to be on the request
		secretName, ok := req.URL.Query()["secret"]
		if !ok || len(secretName[0]) < 1 {
			s.replyReject(rw, req, `missing 'secret=' argument`)
			return
		}
		useBinary, ok := req.URL.Query()["binary"]
		if ok && len(secretName[0]) > 0 {
			useBinaryFlag := useBinary[0] == "1"
			apiReq.Binary = &useBinaryFlag
		}

		// Parse principals body as plaintext
		principalsList, err := ParsePrincipalsFrom(req.Body)
		if err != nil {
			s.replyReject(rw, req, `unable to parse principals: %s`, err.Error())
			return
		}

		// Populate API request struct
		apiReq.Secret = secretName[0]
		apiReq.Principals = principalsList

	}

	// Check if we were given an empty string
	if len(apiReq.Principals) == 0 {
		s.replyReject(rw, req, `given an empty list of principals`)
		return
	}

	// Since our auth token can expire at any time, we are re-connecting on
	// DC/OS on every request. Since we are not expecting any serious request
	// rate on this endpoint,  and since the log-in procedure is quite fast
	// we should be OK
	dclient, err := CreateDCOSClientFromEnvironment()
	if err != nil {
		s.replyReject(rw, req, `Unable to connect to DC/OS: %s`, err.Error())
		return
	}

	// Install missing principals using defaults
	err = s.kadmin.AddMissingPrincipals(apiReq.Principals)
	if err != nil {
		s.replyReject(rw, req, `Unable to add principals: %s`, err.Error())
		return
	}

	// Collect keytab contents
	keytab, err := s.kadmin.GetKeytabForPrincipals(apiReq.Principals)
	if err != nil {
		s.replyReject(rw, req, `Unable to export keytab: %s`, err.Error())
		return
	}

	// Get binary flag
	useBinary := false
	if apiReq.Binary != nil {
		useBinary = *apiReq.Binary
	}

	// Upload to DC/OS secret store
	err = CreateKeytabSecret(dclient, apiReq.Secret, keytab, useBinary)
	if err != nil {
		s.replyReject(rw, req, `Unable to upload to secret store: %s`, err.Error())
		return
	}

	// We are done
	s.replySuccess(rw, req, nil)
}

/**
 * Enumerates the installed principals that match a given wildcard
 */
func (s *KDCAPIServer) handleListPrincipals(rw http.ResponseWriter, req *http.Request) {
	filterExpr := KDCRequestListPrincipals{"*"}

	// We accept only POST
	if req.Method != "POST" {
		s.replyReject(rw, req, `Accepting only POST requests on this endpoint`)
		return
	}

	// Check if we are parsing JSON or plain text
	contentType := req.Header.Get("Content-Type")
	if contentType == "application/json" {
		// We are expeting all the interesting data to be in the payload
		dec := json.NewDecoder(req.Body)
		if err := dec.Decode(&filterExpr); err == io.EOF {
			s.replyReject(rw, req, `Could not decode input`)
			return
		} else if err != nil {
			s.replyReject(rw, req, `Unable to parse request: %s`, err.Error())
			return
		}

	} else {
		// Otherwise we expect the secret name to be on the request
		filterArg, ok := req.URL.Query()["filter"]
		if ok && len(filterArg[0]) > 0 {
			filterExpr.Filter = filterArg[0]
		}
	}

	// Enumerate principals
	list, err := s.kadmin.ListPrincipals(filterExpr.Filter)
	if err != nil {
		s.replyReject(rw, req, `Unable to list principals: %s`, err.Error())
		return
	}

	// Reply the list of arguments
	s.replySuccess(rw, req, list)

}
