package main

import (
	"fmt"
	"github.com/mesosphere/dcos-commons/cli"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
	"net/url"
	"strconv"
)

func main() {
	app, err := cli.NewApp("0.1.0", "Mesosphere", "Manage Apache Kafka framework")
	if err != nil {
		log.Fatalf(err.Error())
	}

	cli.HandleCommonFlags(app, "kafka", "Kafka CLI")
	cli.HandleConfigSection(app)
	cli.HandleEndpointsSection(app)
	cli.HandlePlanSection(app)
	cli.HandlePodsSection(app)
	
	handleBrokerSection(app)
	handleTopicSection(app)

	// Omit modname:
	kingpin.MustParse(app.Parse(cli.GetArguments()))
}



type BrokerHandler struct {
	broker string
}
func (cmd *BrokerHandler) runList(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/brokers"))
	return nil
}
func (cmd *BrokerHandler) runView(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet(fmt.Sprintf("v1/brokers/%s", cmd.broker)))
	return nil
}

func handleBrokerSection(app *kingpin.Application) {
	cmd := &BrokerHandler{}
	broker := app.Command("broker", "Kafka broker information")

	broker.Command(
		"list",
		"Lists all broker ids in the service").Action(cmd.runList)

	get := broker.Command(
		"get",
		"View a single broker").Action(cmd.runView)
	get.Arg("id", "id of broker").StringVar(&cmd.broker)
}


type TopicHandler struct {
	topic string 
	createPartitions int
	createReplication int
	offsetsTime string
	partitionCount int
	produceMessageCount int
}
func (cmd *TopicHandler) runList(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/topics"))		
	return nil
}
func (cmd *TopicHandler) runDescribe(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet(fmt.Sprintf("v1/topics/%s", cmd.topic)))
	return nil
}
func (cmd *TopicHandler) runCreate(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("partitions", strconv.FormatInt(int64(cmd.createPartitions), 10))
	query.Set("replication", strconv.FormatInt(int64(cmd.createReplication), 10))
	cli.PrintJSON(cli.HTTPPutQuery(fmt.Sprintf("v1/topics/%s", cmd.topic), query.Encode()))
	return nil
}
func (cmd *TopicHandler) runUnavailablePartitions(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/topics/unavailable_partitions"))
	return nil
}
func (cmd *TopicHandler) runUnderReplicatedPartitions(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/topics/under_replicated_partitions"))
	return nil
}
func (cmd *TopicHandler) runPartitions(c *kingpin.ParseContext) error {
	query := url.Values{}	
	query.Set("name", cmd.topic)
	query.Set("partitions", strconv.FormatInt(int64(cmd.partitionCount), 10))
	cli.PrintJSON(cli.HTTPPutQuery(fmt.Sprintf("v1/topics/%s/operation/partitions", cmd.topic), query.Encode()))
	return nil
}
func (cmd *TopicHandler) runProducerTest(c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("messages", strconv.FormatInt(int64(cmd.produceMessageCount), 10))
	cli.PrintJSON(cli.HTTPPutQuery(fmt.Sprintf("v1/topics/%s/operation/producer-test", cmd.topic), query.Encode()))
	return nil
}
func (cmd *TopicHandler) runDelete(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPDelete(fmt.Sprintf("v1/topics/%s", cmd.topic)))
	return nil
}
func (cmd *TopicHandler) runOffsets(c *kingpin.ParseContext) error {
	var timeVal int64
	var err error
	switch (cmd.offsetsTime) {
	case "first":
		timeVal = -2
	case "last":
		timeVal = -1
	default:
		timeVal, err = strconv.ParseInt(cmd.offsetsTime, 10, 64)
		if err != nil {
			log.Fatalf("Invalid value '%s' for --time (expected integer, 'first', or 'last'): %s", cmd.offsetsTime, err)
		}
	}
	query := url.Values{}
	query.Set("time", strconv.FormatInt(timeVal, 10))
	cli.PrintJSON(cli.HTTPGetQuery(fmt.Sprintf("v1/topics/%s/offsets", cmd.topic), query.Encode()))
	return nil
}

func handleTopicSection(app *kingpin.Application) {
	cmd := &TopicHandler{}
	topic := app.Command("topic", "Kafka topic maintenance")

	create := topic.Command(
		"create",
		"Creates a new topic").Action(cmd.runCreate)
	create.Arg("topic", "The topic to create").StringVar(&cmd.topic)
	create.Flag("partitions", "Number of partitions").Short('p').Default("1").OverrideDefaultFromEnvar("KAFKA_DEFAULT_PARTITION_COUNT").IntVar(&cmd.createPartitions)
	create.Flag("replication", "Replication factor").Short('r').Default("3").OverrideDefaultFromEnvar("KAFKA_DEFAULT_REPLICATION_FACTOR").IntVar(&cmd.createReplication)

	delete := topic.Command(
		"delete",
		"Deletes an existing topic").Action(cmd.runDelete)
	delete.Arg("topic", "The topic to delete").StringVar(&cmd.topic)

	describe := topic.Command(
		"describe",
		"Describes a single existing topic").Action(cmd.runDescribe)
	describe.Arg("topic", "The topic to describe").StringVar(&cmd.topic)

	topic.Command(
		"list",
		"Lists all available topics").Action(cmd.runList)

	offsets := topic.Command(
		"offsets",
		"Returns the current offset counts for a topic").Action(cmd.runOffsets)
	offsets.Arg("topic", "The topic to examine").StringVar(&cmd.topic)
	offsets.Flag("time", "Offset for the topic: 'first'/'last'/timestamp_millis").Default("last").StringVar(&cmd.offsetsTime)

	partitions := topic.Command(
		"partitions",
		"Alters partition count for an existing topic").Action(cmd.runPartitions)
	partitions.Arg("topic", "The topic to update").StringVar(&cmd.topic)
	partitions.Arg("count", "The number of partitions to assign").IntVar(&cmd.partitionCount)

	producerTest := topic.Command(
		"producer_test",
		"Produces some test messages against a topic").Action(cmd.runProducerTest)
	producerTest.Arg("topic", "The topic to test").StringVar(&cmd.topic)
	producerTest.Arg("messages", "The number of messages to produce").IntVar(&cmd.produceMessageCount)

	topic.Command(
		"unavailable_partitions",
		"Gets info for any unavailable partitions").Action(cmd.runUnavailablePartitions)

	topic.Command(
		"under_replicated_partitions",
		"Gets info for any under-replicated partitions").Action(cmd.runUnderReplicatedPartitions)
}
