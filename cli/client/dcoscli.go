package client

import (
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"os/user"
	"path"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/BurntSushi/toml"
)

var (
	// cachedConfig stores a previously fetched toml config, or is nil.
	cachedConfig map[string]interface{}
)

// RunCLICommand runs a DC/OS CLI command
func RunCLICommand(arg ...string) (string, error) {
	if config.Verbose {
		PrintMessage("Running DC/OS CLI command: dcos %s", strings.Join(arg, " "))
	}
	outBytes, err := exec.Command("dcos", arg...).CombinedOutput()
	if err != nil {
		execErr, ok := err.(*exec.Error)
		if ok && execErr.Err == exec.ErrNotFound {
			// special case: 'dcos' not in PATH. provide special instructions
			PrintMessage("Unable to run DC/OS CLI command: dcos %s", strings.Join(arg, " "))
			PrintMessage("Please perform one of the following fixes, then try again:")
			PrintMessage("- Update your PATH environment to include the path to the 'dcos' executable, or...")
			PrintMessage("- Move the 'dcos' executable into a directory listed in your current PATH (see below).")
			PrintMessageAndExit("Current PATH is: %s", os.Getenv("PATH"))
		}
		return string(outBytes), err
	}
	// trim any trailing newline (or any other whitespace) if present:
	return strings.TrimSpace(string(outBytes)), nil
}

// RequiredCLIConfigValue gets config values from the DC/OS CLI
func RequiredCLIConfigValue(name string, description string, errorInstruction string) string {
	output, err := cliConfigValue(name)
	if err != nil {
		PrintMessage("Unable to retrieve configuration value %s (%s) from CLI. %s:",
			name, description, errorInstruction)
		PrintMessage("Error: %s", err.Error())
		PrintMessage("Output: %s", output)
	}
	if len(output) == 0 {
		PrintMessageAndExit("CLI configuration value %s (%s) is missing/unset. %s",
			name, description, errorInstruction)
	}
	return output
}

// OptionalCLIConfigValue retrieves the CLI configuration for name. If no value can
// be retrieved, this returns an empty string.
func OptionalCLIConfigValue(name string) string {
	output, err := cliConfigValue(name)
	if err != nil {
		// CLI returns an error code when value isn't known
		return ""
	}
	return output
}

// getCLIConfigValue attempts to read the requested config setting from the active
// YAML config file directly before falling back to querying disk
func cliConfigValue(name string) (string, error) {
	userInfo, err := user.Current()
	if err != nil {
		return "", err
	}

	diskConfig, err := cliDiskConfig(userInfo.HomeDir)
	if err == nil {
		return cliDiskConfigValue(diskConfig, name)
	}

	if config.Verbose {
		PrintMessage("No cluster config found, falling back to querying CLI: %s", err.Error())
	}
	// Fall back to querying the CLI directly: slower but works on older CLIs.
	return RunCLICommand("config", "show", name)
}

// cliDiskConfigValue returns a value from the user's CLI configuration on disk,
// or an error if the value could not be retrieved.
func cliDiskConfigValue(diskConfig map[string]interface{}, name string) (string, error) {
	// map "foo.bar.baz" in name to '[foo]\nbar.baz = "..."' in config file
	tokens := strings.SplitN(name, ".", 2)
	if len(tokens) != 2 {
		return "", fmt.Errorf("Unable to tokenize config value into 'section.name': %s => %s", name, tokens)
	}

	rawSection, ok := diskConfig[tokens[0]]
	if !ok {
		return "", fmt.Errorf("No section named '%s' when fetching '%s'", tokens[0], name)
	}
	section, ok := rawSection.(map[string]interface{})
	if !ok {
		return "", fmt.Errorf("Unable to convert content of section '%s' into map", tokens[0])
	}
	rawValue, ok := section[tokens[1]]
	if !ok {
		return "", fmt.Errorf("Unable to retrieve value '%s' from section '%s'", tokens[1], tokens[0])
	}
	value, ok := rawValue.(string)
	if !ok {
		return "", fmt.Errorf("Unable to convert value '%s' from section '%s' into string", tokens[1], tokens[0])
	}
	return value, nil
}

// cliDiskConfig returns the content of the user's CLI configuration on disk,
// or an error if the configuration could not be retrieved.
func cliDiskConfig(userDir string) (map[string]interface{}, error) {
	if cachedConfig != nil {
		return cachedConfig, nil
	}

	// Look for new-style multicluster configs:
	clustersPath := path.Join(userDir, ".dcos", "clusters")
	clusters, err := ioutil.ReadDir(clustersPath)
	if err != nil {
		return nil, err // no clusters directory
	}

	configPath := ""
	for _, cluster := range clusters {
		if !cluster.IsDir() {
			continue // not a directory
		}
		if _, err := os.Stat(path.Join(clustersPath, cluster.Name(), "attached")); os.IsNotExist(err) {
			continue // not the attached/active cluster
		}
		configPath = path.Join(clustersPath, cluster.Name(), "dcos.toml")
		break
	}

	if configPath == "" {
		// No attached multi-cluster entries found. Try old single-cluster config location:
		configPath = path.Join(userDir, ".dcos", "dcos.toml")
	}

	if config.Verbose {
		PrintMessage("Using CLI config file: %s", configPath)
	}

	configData := make(map[string]interface{})
	_, err = toml.DecodeFile(configPath, &configData)
	if err != nil {
		return nil, err
	}
	cachedConfig = configData
	return cachedConfig, nil
}
