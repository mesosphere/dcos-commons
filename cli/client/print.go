package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
)

// PrintMessage is a placeholder function that wraps a call to
// fmt.Println(fmt.Sprintf()) to allow assertions against captured output.
var PrintMessage = printMessage

// PrintMessageAndExit is a placeholder function that wraps a call to
// PrintMessage() before exiting to allow assertions against captured output.
var PrintMessageAndExit = printMessageAndExit

func printMessage(format string, a ...interface{}) (int, error) {
	return fmt.Println(fmt.Sprintf(format, a...))
}

func printMessageAndExit(format string, a ...interface{}) (int, error) {
	PrintMessage(format, a...)
	os.Exit(1)
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
