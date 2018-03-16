package client

import (
	"bytes"
	"encoding/json"
	"fmt"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// PrintMessage is a placeholder function that wraps a call to
// fmt.Println(fmt.Sprintf()) to allow assertions against captured output.
var PrintMessage = printMessage

func printMessage(format string, a ...interface{}) (int, error) {
	return fmt.Printf(format+"\n", a...)
}

// PrintVerbose prints a message using PrintMessage iff config.Verbose is enabled
func PrintVerbose(format string, a ...interface{}) (int, error) {
	if config.Verbose {
		return PrintMessage(format, a...)
	}
	return 0, nil
}

// PrintJSONBytes pretty prints responseBytes assuming it is valid JSON.
// If not valid, an error message will be printed and the original data will
// be printed.
func PrintJSONBytes(responseBytes []byte) {
	var outBuf bytes.Buffer
	err := json.Indent(&outBuf, responseBytes, "", "  ")
	if err != nil {
		// Be permissive of malformed json, such as character codes in strings that are unknown to
		// Go's json: Warn in stderr, then print original to stdout.
		PrintMessage("Failed to prettify JSON response data: %s", err)
		PrintMessage("Original data follows:")
		outBuf = *bytes.NewBuffer(responseBytes)
	}
	PrintMessage("%s", outBuf.String())
}

// PrintResponseText prints out a byte array as text.
func PrintResponseText(body []byte) {
	PrintMessage("%s\n", string(body))
}

// FormatList outputs the provided entries as a list:
// - foo
// - bar
// ...
func FormatList(entries []string) string {
	var buf bytes.Buffer
	for _, element := range entries {
		buf.WriteString(fmt.Sprintf("- %s\n", element))
	}
	// Remove trailing newline:
	if buf.Len() > 0 {
		buf.Truncate(buf.Len() - 1)
	}
	return buf.String()
}
