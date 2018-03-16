package queries

import (
	"github.com/mesosphere/dcos-commons/cli/client"
)

type Endpoints struct {
	PrefixCb func() string
}

func NewEndpoints() *Endpoints {
	return &Endpoints{
		PrefixCb: func() string { return "v1/" },
	}
}

func (q *Endpoints) Show(endpointName string) error {
	responseBytes, err := client.HTTPServiceGet(q.PrefixCb() + "endpoints/" + endpointName)
	if err != nil {
		return err
	}
	// Endpoint details may be of any format, so just print the raw text
	client.PrintResponseText(responseBytes)
	return nil
}

func (q *Endpoints) List() error {
	responseBytes, err := client.HTTPServiceGet(q.PrefixCb() + "endpoints")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(responseBytes)
	return nil
}
