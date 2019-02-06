package queries

import (
	"github.com/mesosphere/dcos-commons/cli/client"
	"log"
)

type Config struct {
	PrefixCb func() string
}

func NewConfig() *Config {
	return &Config{
		PrefixCb: func() string { return "v1/" },
	}
}

func (q *Config) List() error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "configurations")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *Config) Show(configId string) error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "configurations/" + configId)
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *Config) Target() error {
	log.Printf("DELETEME@kjoshi Config::Target called!\n")
	body, err := client.HTTPServiceGet(q.PrefixCb() + "configurations/target")
	if err != nil {
		log.Printf("DELETEME@kjoshi Error at configurations/target err:\n%s\nbody:%s\n", err, body)
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}

func (q *Config) TargetID() error {
	body, err := client.HTTPServiceGet(q.PrefixCb() + "configurations/targetId")
	if err != nil {
		return err
	}
	client.PrintJSONBytes(body)
	return nil
}
