package queries

import (
	"github.com/mesosphere/dcos-commons/cli/client"
)

type State struct {
	PrefixCb func() string
}

func NewState() *State {
	return &State{
		PrefixCb: func() string { return "v1/" },
	}
}

func (q *State) FrameworkID() error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "state/frameworkId")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *State) ListProperties() error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "state/properties")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *State) Property(propName string) error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "state/properties/" + propName)
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *State) RefreshCache() error {
	body, err := client.HTTPServicePut(q.PrefixCb() + "state/refresh")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}
