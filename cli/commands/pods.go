package commands

import (
	"bytes"
	"fmt"

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
	if len(cmd.PodName) > 0 {
		endpointPath = fmt.Sprintf("v1/pod/%s/status", cmd.PodName)
	} else {
		endpointPath = "v1/pod/status"
	}
	body, err := client.HTTPServiceGet(endpointPath)
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	}
	if cmd.RawJSON || len(cmd.PodName) > 0 {
		client.PrintJSONBytes(body)
	} else {
		client.PrintMessage(toPodTree(body))
	}
	return nil
}

func toPodTree(podsJSONBytes []byte) string {
	response, err := client.UnmarshalJSON(podsJSONBytes)
	if err != nil {
		client.PrintMessageAndExit(fmt.Sprintf("Failed to parse JSON in pods response: %s", err))
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", response["service"]))

	i := 0
	rawPods, ok := response["pods"].([]interface{})
	if !ok {
		client.PrintMessageAndExit("Failed to parse JSON pods in response")
	}
	for _, rawPod := range rawPods {
		appendPod(&buf, rawPod, i == len(rawPods)-1)
		i++
	}

	// Trim extra newline from end:
	buf.Truncate(buf.Len() - 1)

	return buf.String()
}

func appendPod(buf *bytes.Buffer, rawPod interface{}, lastPod bool) {
	pod, ok := rawPod.(map[string]interface{})
	if !ok {
		return
	}

	var prefix string
	if lastPod {
		prefix = "└─ "
	} else {
		prefix = "├─ "
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", prefix, pod["name"]))

	rawPodInstances, ok := pod["instances"].([]interface{})
	if !ok {
		return
	}
	for i, rawPodInstance := range rawPodInstances {
		appendPodInstance(buf, rawPodInstance, lastPod, i == len(rawPodInstances)-1)
	}
}

func appendPodInstance(buf *bytes.Buffer, rawPodInstance interface{}, lastPod bool, lastPodInstance bool) {
	podInstance, ok := rawPodInstance.(map[string]interface{})
	if !ok {
		return
	}

	var prefix string
	if lastPod {
		if lastPodInstance {
			prefix = "   └─ "
		} else {
			prefix = "   ├─ "
		}
	} else {
		if lastPodInstance {
			prefix = "│  └─ "
		} else {
			prefix = "│  ├─ "
		}
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", prefix, podInstance["name"]))

	tasks, ok := podInstance["tasks"].([]interface{})
	if !ok {
		return
	}
	for i, rawTask := range tasks {
		appendTask(buf, rawTask, lastPod, lastPodInstance, i == len(tasks)-1)
	}
}

func appendTask(buf *bytes.Buffer, rawTask interface{}, lastPod bool, lastPodInstance bool, lastTask bool) {
	task, ok := rawTask.(map[string]interface{})
	if !ok {
		return
	}

	var prefix string
	if lastPod {
		if lastPodInstance {
			if lastTask {
				prefix = "      └─ "
			} else {
				prefix = "      ├─ "
			}
		} else {
			if lastTask {
				prefix = "   │  └─ "
			} else {
				prefix = "   │  ├─ "
			}
		}
	} else {
		if lastPodInstance {
			if lastTask {
				prefix = "│     └─ "
			} else {
				prefix = "│     ├─ "
			}
		} else {
			if lastTask {
				prefix = "│  │  └─ "
			} else {
				prefix = "│  │  ├─ "
			}
		}
	}

	taskName, ok := task["name"]
	if !ok {
		taskName = "<UNKNOWN>"
	}
	taskState, ok := task["status"]
	if !ok {
		taskState = "<UNKNOWN>"
	}
	buf.WriteString(fmt.Sprintf("%s%s (%s)\n", prefix, taskName, taskState))
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
