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

type podTaskResponse struct {
	Name   string `json:"name"`
	Status string `json:"status"`
}

type podInstanceResponse struct {
	Name  string            `json:"name"`
	Tasks []podTaskResponse `json:"tasks"`
}

type podTypeResponse struct {
	Name      string                `json:"name"`
	Instances []podInstanceResponse `json:"instances"`
}

type serviceResponse struct {
	Service string            `json:"service"`
	Pods    []podTypeResponse `json:"pods"`
}

func toServiceTree(podsBytes []byte) (string, error) {
	service := serviceResponse{}
	err := json.Unmarshal(podsBytes, &service)
	if err != nil {
		return "", fmt.Errorf("Failed to parse JSON in pods response: %s", err)
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", service.Service))
	for i, podType := range service.Pods {
		appendPodType(&buf, &podType, i == len(service.Pods)-1)
	}

	return strings.TrimRight(buf.String(), "\n"), nil
}

func toSinglePodTree(podsBytes []byte) (string, error) {
	instance := podInstanceResponse{}
	err := json.Unmarshal(podsBytes, &instance)
	if err != nil {
		return "", fmt.Errorf("Failed to parse JSON in pod response: %s", err)
	}

	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("%s\n", instance.Name))
	for i, task := range instance.Tasks {
		appendTask(&buf, &task, "", i == len(instance.Tasks)-1)
	}

	return strings.TrimRight(buf.String(), "\n"), nil
}

func appendPodType(buf *bytes.Buffer, podType *podTypeResponse, lastPodType bool) {
	var headerPrefix string
	var childPrefix string
	if lastPodType {
		headerPrefix = "└─ "
		childPrefix = "   "
	} else {
		headerPrefix = "├─ "
		childPrefix = "│  "
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", headerPrefix, podType.Name))
	for i, instance := range podType.Instances {
		appendPodInstance(buf, &instance, childPrefix, i == len(podType.Instances)-1)
	}
}

func appendPodInstance(buf *bytes.Buffer, instance *podInstanceResponse, prefix string, lastPodInstance bool) {
	var headerPrefix string
	var childPrefix string
	if lastPodInstance {
		headerPrefix = prefix + "└─ "
		childPrefix = prefix + "   "
	} else {
		headerPrefix = prefix + "├─ "
		childPrefix = prefix + "│  "
	}

	buf.WriteString(fmt.Sprintf("%s%s\n", headerPrefix, instance.Name))

	for i, task := range instance.Tasks {
		appendTask(buf, &task, childPrefix, i == len(instance.Tasks)-1)
	}
}

func appendTask(buf *bytes.Buffer, task *podTaskResponse, prefix string, lastTask bool) {
	if lastTask {
		prefix += "└─ "
	} else {
		prefix += "├─ "
	}
	if len(task.Name) == 0 {
		task.Name = unknownValue
	}
	if len(task.Status) == 0 {
		task.Status = unknownValue
	}
	buf.WriteString(fmt.Sprintf("%s%s (%s)\n", prefix, task.Name, task.Status))
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
