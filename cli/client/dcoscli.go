package client

import (
	"log"
	"os"
	"os/exec"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// TODO(nick): Consider breaking this config retrieval out into a separate independent library?

func RunCLICommand(arg ...string) (string, error) {
	if config.Verbose {
		log.Printf("Running DC/OS CLI command: dcos %s", strings.Join(arg, " "))
	}
	outBytes, err := exec.Command("dcos", arg...).CombinedOutput()
	if err != nil {
		execErr, ok := err.(*exec.Error)
		if ok && execErr.Err == exec.ErrNotFound {
			// special case: 'dcos' not in PATH. provide special instructions
			log.Printf("Unable to run DC/OS CLI command: dcos %s", strings.Join(arg, " "))
			log.Printf("Please perform one of the following fixes, then try again:")
			log.Printf("- Update your PATH environment to include the path to the 'dcos' executable, or...")
			log.Printf("- Move the 'dcos' executable into a directory listed in your current PATH (see below).")
			log.Fatalf("Current PATH is: %s", os.Getenv("PATH"))
		}
		return string(outBytes), err
	}
	// trim any trailing newline (or any other whitespace) if present:
	return strings.TrimSpace(string(outBytes)), nil
}

func RequiredCLIConfigValue(name string, description string, errorInstruction string) string {
	output, err := RunCLICommand("config", "show", name)
	if err != nil {
		log.Printf("Unable to retrieve configuration value %s (%s) from CLI. %s:",
			name, description, errorInstruction)
		log.Printf("Error: %s", err.Error())
		log.Printf("Output: %s", output)
	}
	if len(output) == 0 {
		log.Fatalf("CLI configuration value %s (%s) is missing/unset. %s",
			name, description, errorInstruction)
	}
	return output
}

func OptionalCLIConfigValue(name string) string {
	output, err := RunCLICommand("config", "show", name)
	if err != nil {
		// CLI returns an error code when value isn't known
		return ""
	}
	return output
}
