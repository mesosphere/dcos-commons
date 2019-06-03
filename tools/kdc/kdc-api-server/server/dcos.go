package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"github.com/dcos/client-go/dcos"
	"net/url"
	"os"
	"strings"
)

/**
 * Create a new DC/OS client instance, by authenticating against the cluster
 * using the service account secret given by the environment
 */
func createDCOSClientFromEnvironment() (*dcos.APIClient, error) {
	// Get the contents of the service account secret
	secretContents := []byte(os.Getenv("SERVICE_ACCOUNT_SECRET"))
	if len(secretContents) == 0 {
		return nil, fmt.Errorf("Missing SERVICE_ACCOUNT_SECRET environment variable")
	}

	// Parse secret contents into a service account login object
	var saConfig dcos.ServiceAccountOptions
	err := json.Unmarshal(secretContents, &saConfig)
	if err != nil {
		return nil, fmt.Errorf("Unable to parse the service account secret: %s", err.Error())
	}

	// Extract cluster URL from auth token URL
	url, err := url.Parse(saConfig.LoginEndpoint)
	if err != nil {
		return nil, fmt.Errorf("Unable to compute the cluster URL: %s", err.Error())
	}
	url.RawQuery = ""
	url.Fragment = ""
	url.Path = "/"
	clusterURL := url.String()

	// Create a blank new cluent
	config := dcos.NewConfig(nil)
	config.SetURL(clusterURL)
	client, err := dcos.NewClientWithConfig(config)
	if err != nil {
		return nil, fmt.Errorf("Unable to create a DC/OS client: %s", err.Error())
	}

	// Login now
	authToken, _, err := client.LoginWithServiceAccount(context.TODO(), saConfig)
	if err != nil {
		return nil, fmt.Errorf("Unable to authenticate: %s", err.Error())
	}

	// Update configuration object (it's passed by reference)
	config.SetACSToken(authToken.Token)
	return client, nil
}

/**
 * CreateSecret Creates a secret on the DC/OS secret store
 */
func CreateKeytabSecret(client *dcos.APIClient, secretName string, keytab []byte, binary bool) error {
	// Convert to base64 if binary is false
	var secret dcos.SecretsV1Secret
	if !binary {
		secret = dcos.SecretsV1Secret{Value: base64.StdEncoding.EncodeToString(keytab)}
		secretName = fmt.Sprintf("__dcos_base64__%s", secretName)
	} else {
		secret = dcos.SecretsV1Secret{Value: string(keytab)}
	}

	// Try to create the secret on DC/OS
	_, err := client.Secrets.CreateSecret(context.TODO(), "default", secretName, secret)
	if err != nil {

		// If this was a conflict, replace the secret
		if strings.Contains(err.Error(), "Conflict") {
			_, err := client.Secrets.UpdateSecret(context.TODO(), "default", secretName, secret)
			return err
		}

		return err
	}

	return nil
}
