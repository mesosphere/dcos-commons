package client

import (
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"path"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
	"github.com/BurntSushi/toml"
)

const dcosConfigDirName = ".dcos"
const dcosConfigFileName = "dcos.toml"

var (
	// cachedConfig stores a previously fetched toml config, or is nil.
	cachedConfig map[string]interface{}
)

// RunCLICommand runs a DC/OS CLI command
func RunCLICommand(arg ...string) (string, error) {
	PrintVerbose("Running DC/OS CLI command: dcos %s", strings.Join(arg, " "))
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
		PrintMessageAndExit("Output: %s", output)
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

// homeDir attempts to retrieve the DC/OS CLI config directory, or returns an error
// if retrieval fails
func configDir() (string, error) {
	// At the moment (go1.8.3), os.user.Current() just produces: "user: Current not implemented on YOUROS/YOURARCH"
	// Apparently this is due to lack of support with cross-compiled binaries? Therefore we DIY it here.

	// DC/OS CLI allows users to manually override the config dir (default ~/.dcos) with a DCOS_DIR envvar:
	configDir := config.DcosConfigRootDir // proxy for DCOS_DIR envvar
	if len(configDir) != 0 {
		return configDir, nil
	}

	// OSX/Linux: $HOME/.dcos/
	homeDir := os.Getenv("HOME")
	if len(homeDir) != 0 {
		return path.Join(homeDir, dcosConfigDirName), nil
	}

	// Windows: ${HOMEDRIVE}${HOMEPATH}/.dcos/ or $USERPROFILE/.dcos/
	homeDrive := os.Getenv("HOMEDRIVE")
	homePath := os.Getenv("HOMEPATH")
	if len(homeDrive) != 0 && len(homePath) != 0 {
		return path.Join(homeDrive + homePath, dcosConfigDirName), nil
	}
	homeDir = os.Getenv("USERPROFILE")
	if len(homeDir) != 0 {
		return path.Join(homeDir, dcosConfigDirName), nil
	}

	// If we get here, it means that we couldn't figure out the user's home directory.
	// Shouldn't happen in practice.
	return "", fmt.Errorf("Unable to resolve CLI config directory: DCOS_DIR, HOME, HOMEDRIVE+HOMEPATH, or USERPROFILE")
}

// cliConfigValue attempts to retrieve the requested config setting from one of the following places:
// 1. DCOS_* envvar override
// 2. CLI config file (dcos.toml)
// 3. Running 'dcos config show <name>'
func cliConfigValue(name string) (string, error) {
	// dcos-cli supports envvar overrides of config file settings, using one of several conventions:
	envVal := cliEnvConfigValue(name)
	if len(envVal) > 0 {
		return envVal, nil
	}

	// dcos-cli allows providing a custom envvar for the config path, we honor that here:
	configDir, err := configDir()
	if err != nil {
		PrintVerbose("Falling back to querying CLI for %s: %s", name, err.Error())
	} else {
		diskConfig, err := cliDiskConfig(configDir)
		if err == nil {
			return cliDiskConfigValue(diskConfig, name)
		} else {
			PrintVerbose("No cluster config found, falling back to querying CLI for %s: %s", name, err.Error())
		}
	}

	// If no value was listed in the env override and no cluster config file was found,
	// give up and fall back to querying the CLI directly. This is much slower than the above
	// methods but is a reasonable worst-case fallback.
	return RunCLICommand("config", "show", name)
}

func cliEnvConfigValue(name string) string {
	// dcos-cli supports envvar overrides of config file settings, using one of the following conventions:
	envName := ""
	envVal := ""
	if strings.HasPrefix(name, "core.dcos_") {
		// core.dcos_foo_bar => DCOS_FOO_BAR or DCOS_DCOS_FOO_BAR
		// (so e.g. core.dcos_url could be DCOS_DCOS_URL or just DCOS_URL)
		envName = "DCOS_" + strings.ToUpper(strings.TrimPrefix(name, "core.dcos_")) // DCOS_FOO_BAR
		envVal = os.Getenv(envName)
		if len(envVal) == 0 {
			envName = "DCOS_" + envName // DCOS_DCOS_FOO_BAR
			envVal = os.Getenv(envName)
		}
	} else if strings.HasPrefix(name, "core.") {
		// core.foo_bar => DCOS_FOO_BAR
		envName = "DCOS_" + strings.ToUpper(strings.TrimPrefix(name, "core."))
		envVal = os.Getenv(envName)
	} else {
		// other.foo_bar => DCOS_OTHER_FOO_BAR
		envName = "DCOS_" + strings.ToUpper(strings.Replace(name, ".", "_", -1))
		envVal = os.Getenv(envName)
	}
	if len(envVal) != 0 {
		PrintVerbose("Using provided envvar %s for config value %s=%s", envName, name, envVal)
		return envVal
	}
	return ""
}

// cliDiskConfigValue returns a value from the user's CLI configuration on disk,
// or an error if the value could not be retrieved.
func cliDiskConfigValue(diskConfig map[string]interface{}, name string) (string, error) {
	// map "foo.bar.baz" in name to '[foo]\nbar.baz = "..."' in config file
	tokens := strings.SplitN(name, ".", 2)
	if len(tokens) != 2 {
		return "", fmt.Errorf("Unable to tokenize config value into 'section.name' format: %s => %s", name, tokens)
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
func cliDiskConfig(configDir string) (map[string]interface{}, error) {
	if cachedConfig != nil {
		return cachedConfig, nil
	}

	// Find the "dcos.toml" config file:
	configPath := ""

	// Look for new-style multicluster configs:
	clustersPath := path.Join(configDir, "clusters")
	clusters, err := ioutil.ReadDir(clustersPath)
	if err == nil {
		attachedClusterNameOverride := config.DcosClusterName // custom cluster name (override attach bit)
		if len(attachedClusterNameOverride) != 0 {
			// Cluster name defined, find the config with a matching name.
			for _, cluster := range clusters {
				if !cluster.IsDir() {
					continue // not a directory
				}
				configPath = path.Join(clustersPath, cluster.Name(), dcosConfigFileName)

				configData := make(map[string]interface{})
				_, err = toml.DecodeFile(configPath, &configData)
				if err != nil {
					continue
				}
				name, err := cliDiskConfigValue(configData, "cluster.name")
				if err == nil && name == attachedClusterNameOverride {
					// Matching name found. Use this config.
					cachedConfig = configData
					return configData, nil
				}
			}
			return nil, fmt.Errorf("Unable to find a cluster config named %s (DCOS_CLUSTER)",
				attachedClusterNameOverride)
		} else {
			// No DCOS_CLUSTER defined, fall back to finding the directory with an "attached" file
			for _, cluster := range clusters {
				if !cluster.IsDir() {
					continue // not a directory
				}
				if _, err := os.Stat(path.Join(clustersPath, cluster.Name(), "attached")); os.IsNotExist(err) {
					continue // not the attached/active cluster
				}
				if len(configPath) != 0 {
					return nil, fmt.Errorf("Multiple configs have an 'attached' flag, unable to decide which to use")
				}
				configPath = path.Join(clustersPath, cluster.Name(), dcosConfigFileName)
			}
		}
	}

	// No new-style multicluster config found, fall back to old-style single config handling:
	if len(configPath) == 0 {
		configPath = config.DcosConfigPath // custom config path, proxy for 0.4.x DCOS_CONFIG
		if len(configPath) == 0 {
			configPath = path.Join(configDir, dcosConfigFileName)
		}
		PrintVerbose("No config found in new location: %s, falling back to old location: %s", clustersPath, configPath)
	}

	PrintVerbose("Using CLI config file: %s", configPath)

	configData := make(map[string]interface{})
	_, err = toml.DecodeFile(configPath, &configData)
	if err != nil {
		return nil, err
	}
	cachedConfig = configData
	return cachedConfig, nil
}
