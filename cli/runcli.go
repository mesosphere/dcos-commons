package cli

import (
	"log"
	"os"
	"os/exec"
	"strings"
)

// TODO(nick): Consider breaking this config retrieval out into a separate independent library?

func RunDCOSCLICommand(arg ...string) (string, error) {
	outBytes, err := exec.Command("dcos", arg...).CombinedOutput()
	if err != nil {
		if Verbose {
			log.Printf("PATH: %s", os.Getenv("PATH"))
		}
		return string(outBytes), err
	}
	// trim any trailing newline (or any other whitespace) if present:
	return strings.TrimSpace(string(outBytes)), nil
}

func GetCheckedDCOSCLIConfigValue(name string, description string, errorInstruction string) string {
	output, err := RunDCOSCLICommand("config", "show", name)
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
