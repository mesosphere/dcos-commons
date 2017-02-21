package cli

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
)

func PrintText(response *http.Response) {
	fmt.Fprintf(os.Stdout, "%s\n", GetResponseText(response))
}

func PrintJSONBytes(responseBytes []byte, request *http.Request) {
	var outBuf bytes.Buffer
	err := json.Indent(&outBuf, responseBytes, "", "  ")
	if err != nil {
		// Be permissive of malformed json, such as character codes in strings that are unknown to
		// Go's json: Warn in stderr, then print original to stdout.
		log.Printf("Failed to prettify JSON response data from %s %s query: %s",
			request.Method, request.URL, err)
		log.Printf("Original data follows:")
		outBuf = *bytes.NewBuffer(responseBytes)
	}
	outBuf.WriteTo(os.Stdout)
	fmt.Print("\n")
}

func PrintJSON(response *http.Response) {
	PrintJSONBytes(GetResponseBytes(response), response.Request)
}

func GetResponseText(response *http.Response) string {
	return string(GetResponseBytes(response))
}

func GetResponseBytes(response *http.Response) []byte {
	defer response.Body.Close()
	responseBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		log.Fatalf("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	return responseBytes
}
