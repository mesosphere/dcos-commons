package commands

import (
	"bytes"
	"fmt"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type podHandler struct {
	PodName string
	RawJSON bool
}

func (cmd *podHandler) handleList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	body, err := client.HTTPServiceGet("v1/pod")
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *podHandler) handleStatus(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	var endpointPath string
	singlePod := len(cmd.PodName) > 0
	if singlePod {
		endpointPath = fmt.Sprintf("v1/pod/%s/status", cmd.PodName)
	} else {
		endpointPath = "v1/pod/status"
	}
	body, err := client.HTTPServiceGet(endpointPath)
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if cmd.RawJSON {
		client.PrintJSONBytes(body)
	} else {
		if singlePod {
			client.PrintMessage(toSinglePodTree(body))
		} else {
			client.PrintMessage(toServiceTree(body))
		}
	}
	return nil
}

func toServiceTree(podsJSONBytes []byte) string {
	response, err := client.UnmarshalJSON(podsJSONBytes)
	if err != nil {
		client.PrintMessageAndExit(fmt.Sprintf("Failed to parse JSON in pods response: %s", err))
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", response["service"]))

	rawPodTypes, ok := response["pods"].([]interface{})
	if !ok {
		client.PrintMessageAndExit("Failed to parse JSON pods in response")
	}
	for i, rawPodType := range rawPodTypes {
		appendPodType(&buf, rawPodType, i == len(rawPodTypes)-1)
	}

	return strings.TrimRight(buf.String(), "\n")
}

func toSinglePodTree(podsJSONBytes []byte) string {
	response, err := client.UnmarshalJSON(podsJSONBytes)
	if err != nil {
		client.PrintMessageAndExit(fmt.Sprintf("Failed to parse JSON in pod response: %s", err))
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", response["name"]))

	rawTasks, ok := response["tasks"].([]interface{})
	if !ok {
		client.PrintMessageAndExit("Failed to parse JSON pods in response")
	}
	for i, rawTask := range rawTasks {
		appendTask(&buf, rawTask, "", i == len(rawTasks)-1)
	}

	return strings.TrimRight(buf.String(), "\n")
}

func appendPodLayer(buf *bytes.Buffer, rawPodType interface{}, lastPodType bool) {
	podType, ok := rawPodType.(map[string]interface{})
	if !ok {
		return
	}

	var headerPrefix string
	var childPrefix string
	if lastPodType {
		headerPrefix = "└─ "
		childPrefix = "   "
	} else {
		headerPrefix = "├─ "
		childPrefix = "│  "
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", headerPrefix, podType["name"]))

	rawPodInstances, ok := podType["instances"].([]interface{})
	if !ok {
		return
	}
	for i, rawPodInstance := range rawPodInstances {
		appendPodInstance(buf, rawPodInstance, childPrefix, i == len(rawPodInstances)-1)
	}
}

func appendPodType(buf *bytes.Buffer, rawPodType interface{}, lastPodType bool) {
	podType, ok := rawPodType.(map[string]interface{})
	if !ok {
		return
	}

	var headerPrefix string
	var childPrefix string
	if lastPodType {
		headerPrefix = "└─ "
		childPrefix = "   "
	} else {
		headerPrefix = "├─ "
		childPrefix = "│  "
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", headerPrefix, podType["name"]))

	rawPodInstances, ok := podType["instances"].([]interface{})
	if !ok {
		return
	}
	for i, rawPodInstance := range rawPodInstances {
		appendPodInstance(buf, rawPodInstance, childPrefix, i == len(rawPodInstances)-1)
	}
}

func appendPodInstance(buf *bytes.Buffer, rawPodInstance interface{}, prefix string, lastPodInstance bool) {
	podInstance, ok := rawPodInstance.(map[string]interface{})
	if !ok {
		return
	}

	var headerPrefix string
	var childPrefix string
	if lastPodInstance {
		headerPrefix = prefix + "└─ "
		childPrefix = prefix + "   "
	} else {
		headerPrefix = prefix + "├─ "
		childPrefix = prefix + "│  "
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", headerPrefix, podInstance["name"]))

	tasks, ok := podInstance["tasks"].([]interface{})
	if !ok {
		return
	}
	for i, rawTask := range tasks {
		appendTask(buf, rawTask, childPrefix, i == len(tasks)-1)
	}
}

func appendTask(buf *bytes.Buffer, rawTask interface{}, prefix string, lastTask bool) {
	task, ok := rawTask.(map[string]interface{})
	if !ok {
		return
	}

	if lastTask {
		prefix += "└─ "
	} else {
		prefix += "├─ "
	}

	taskName, ok := task["name"]
	if !ok {
		taskName = "<UNKNOWN>"
	}
	taskStatus, ok := task["status"]
	if !ok {
		taskStatus = "<UNKNOWN>"
	}

	buf.WriteString(fmt.Sprintf("%s%s (%s)\n", prefix, taskName, taskStatus))
}

func (cmd *podHandler) handleInfo(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	body, err := client.HTTPServiceGet(fmt.Sprintf("v1/pod/%s/info", cmd.PodName))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintJSONBytes(body)
	return nil
}

func (cmd *podHandler) handleRestart(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	body, err := client.HTTPServicePost(fmt.Sprintf("v1/pod/%s/restart", cmd.PodName))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintResponseText(body)

	return nil
}

func (cmd *podHandler) handleReplace(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	body, err := client.HTTPServicePost(fmt.Sprintf("v1/pod/%s/replace", cmd.PodName))
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	client.PrintResponseText(body)
	return nil
}

// HandlePodSection adds pod subcommands to the passed in kingpin.Application.
func HandlePodSection(app *kingpin.Application) {
	// pod[s] [status [name], info <name>, restart <name>, replace <name>]
	cmd := &podHandler{}
	pod := app.Command("pod", "View Pod/Task state")

	pod.Command("list", "Display the list of known pod instances").Action(cmd.handleList)

	status := pod.Command("status", "Display the status for tasks in one pod or all pods").Action(cmd.handleStatus)
	status.Arg("pod", "Name of a specific pod instance to display").StringVar(&cmd.PodName)
	status.Flag("json", "Show raw JSON response instead of user-friendly tree").BoolVar(&cmd.RawJSON)

	info := pod.Command("info", "Display the full state information for tasks in a pod").Action(cmd.handleInfo)
	info.Arg("pod", "Name of the pod instance to display").Required().StringVar(&cmd.PodName)

	restart := pod.Command("restart", "Restarts a given pod without moving it to a new agent").Action(cmd.handleRestart)
	restart.Arg("pod", "Name of the pod instance to restart").Required().StringVar(&cmd.PodName)

	replace := pod.Command("replace", "Destroys a given pod and moves it to a new agent").Action(cmd.handleReplace)
	replace.Arg("pod", "Name of the pod instance to replace").Required().StringVar(&cmd.PodName)
}
