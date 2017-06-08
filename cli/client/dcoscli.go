package client

import (
	"os"
	"os/exec"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// TODO(nick): Consider breaking this config retrieval out into a separate independent library?

// RunCLICommand is used to run generic commands against the DC/OS CLI.
// It attempts to run the `dcos` executable contained within the user's PATH.
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

// RequiredCLIConfigValue retrieves the CLI configuration property for name. If no value can
// be retrieved, this terminates with a fatal error.
func RequiredCLIConfigValue(name string, description string, errorInstruction string) string {
	output, err := RunCLICommand("config", "show", name)
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
	output, err := RunCLICommand("config", "show", name)
	if err != nil {
		// CLI returns an error code when value isn't known
		return ""
	}
	return output
}
