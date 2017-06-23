package client

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
)

type responseCheck func(response *http.Response, body []byte) error

var customCheck responseCheck

// SetCustomResponseCheck sets a custom responseCheck that
// can be used for more advanced handling of HTTP responses.
func SetCustomResponseCheck(check responseCheck) {
	customCheck = check
}

func checkHTTPResponse(response *http.Response) ([]byte, error) {
	body, err := getResponseBytes(response)
	if err != nil {
		return nil, fmt.Errorf("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	if customCheck != nil {
		err := customCheck(response, body)
		if err != nil {
			return body, err
		}
	}
	err = defaultResponseCheck(response)
	return body, err
}

func defaultResponseCheck(response *http.Response) error {
	switch {
	case response.StatusCode == http.StatusUnauthorized:
		errorString := `Got 401 Unauthorized response from %s
"- Bad auth token? Run 'dcos auth login' to log in.`
		return fmt.Errorf(errorString, response.Request.URL)
	case response.StatusCode == http.StatusInternalServerError || response.StatusCode == http.StatusBadGateway || response.StatusCode == http.StatusNotFound:
		return createServiceNameError(response)
	case response.StatusCode < 200 || response.StatusCode >= 300:
		return createResponseError(response)
	}
	return nil
}

func getResponseBytes(response *http.Response) ([]byte, error) {
	defer response.Body.Close()
	responseBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	return responseBytes, nil
}

// UnmarshalJSON unmarshals a []byte of JSON into a map[string]interface{}
// for easy handling of JSON responses that have variable field.
func UnmarshalJSON(jsonBytes []byte) (map[string]interface{}, error) {
	var responseJSON map[string]interface{}
	err := json.Unmarshal(jsonBytes, &responseJSON)
	if err != nil {
		return nil, err
	}
	return responseJSON, nil
}

// GetValueFromJSONResponse is a utility function to unmarshal a []byte of JSON
// and fetch the value of a specific field from it.
func GetValueFromJSONResponse(responseBytes []byte, field string) ([]byte, error) {
	responseJSONBytes, err := UnmarshalJSON(responseBytes)
	if err != nil {
		return nil, err
	}
	return GetValueFromJSON(responseJSONBytes, field)
}

// GetValueFromJSON retrieves the value of a specific field from an unmarshaled map[string]interface
// of JSON. If the field does not exist, this returns nil.
func GetValueFromJSON(jsonBytes map[string]interface{}, field string) ([]byte, error) {
	if valueJSON, present := jsonBytes[field]; present {
		valueBytes, err := json.Marshal(valueJSON)
		if err != nil {
			return nil, err
		}
		return valueBytes, nil
	}
	return nil, nil
}

// ConvertToStringArray unmarshals a byte array of JSON into a string array
func ConvertJSONToStringArray(bytes []byte) ([]string, error) {
	var convertedArray []string
	err := json.Unmarshal(bytes, &convertedArray)
	if err != nil {
		return nil, err
	}
	return convertedArray, nil
}
