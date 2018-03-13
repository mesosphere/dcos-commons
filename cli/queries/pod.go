package queries

import (
	"bytes"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/client"
)

type Pod struct {
	PrefixCb func() string
}

func NewPod() *Pod {
	return &Pod{
		PrefixCb: func() string { return "v1/" },
	}
}

func (q *Pod) List() error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "pod")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *Pod) Status(podName string, rawJSON bool) error {
	var endpointPath string
	singlePod := len(podName) > 0
	if singlePod {
		endpointPath = fmt.Sprintf("%spod/%s/status", q.PrefixCb(), podName)
	} else {
		endpointPath = q.PrefixCb() + "pod/status"
	}
	body, err := client.HTTPServiceGet(endpointPath)
	if err != nil {
		return err
	}
	if rawJSON {
		client.PrintJSONBytes(body)
	} else {
		var tree string
		if singlePod {
			tree, err = toSinglePodTree(body)
		} else {
			tree, err = toServiceTree(body)
		}
		if err != nil {
			return err
		}
		client.PrintMessage(tree)
	}
	return nil
}

func toServiceTree(podsJSONBytes []byte) (string, error) {
	response, err := client.UnmarshalJSON(podsJSONBytes)
	if err != nil {
		return "", fmt.Errorf("Failed to parse JSON in pods response: %s", err)
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", response["service"]))

	rawPodTypes, ok := response["pods"].([]interface{})
	if !ok {
		return "", fmt.Errorf("Failed to parse JSON pods in response")
	}
	for i, rawPodType := range rawPodTypes {
		appendPodType(&buf, rawPodType, i == len(rawPodTypes)-1)
	}

	return strings.TrimRight(buf.String(), "\n"), nil
}

func toSinglePodTree(podsJSONBytes []byte) (string, error) {
	response, err := client.UnmarshalJSON(podsJSONBytes)
	if err != nil {
		return "", fmt.Errorf("Failed to parse JSON in pod response: %s", err)
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", response["name"]))

	rawTasks, ok := response["tasks"].([]interface{})
	if !ok {
		return "", fmt.Errorf("Failed to parse JSON pods in response")
	}
	for i, rawTask := range rawTasks {
		appendTask(&buf, rawTask, "", i == len(rawTasks)-1)
	}

	return strings.TrimRight(buf.String(), "\n"), nil
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

func (q *Pod) Info(podName string) error {
	body, err := client.HTTPServiceGet(fmt.Sprintf("%spod/%s/info", q.PrefixCb(), podName))
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

// "restart", "replace", "pause", "resume"
func (q *Pod) Command(podCmd, podName string, taskNames []string) error {
	var err error
	var body []byte
	if len(taskNames) == 0 {
		body, err = client.HTTPServicePost(fmt.Sprintf("%spod/%s/%s", q.PrefixCb(), podName, podCmd))
	} else {
		var payload []byte
		payload, err = json.Marshal(taskNames)
		if err != nil {
			return err
		}
		body, err = client.HTTPServicePostJSON(fmt.Sprintf("%spod/%s/%s", q.PrefixCb(), podName, podCmd), payload)
	}
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}
