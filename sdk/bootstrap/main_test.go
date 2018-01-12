package main

import "io/ioutil"
import "log"
import "os"
import "testing"
import "github.com/stretchr/testify/assert"

// Sample config template used by the Cassandra service
var cassandraTemplate = `dc={{CASSANDRA_LOCATION_DATA_CENTER}}
{{#PLACEMENT_REFERENCED_ZONE}}
rack={{ZONE}}
{{/PLACEMENT_REFERENCED_ZONE}}
{{^PLACEMENT_REFERENCED_ZONE}}
rack=rack1
{{/PLACEMENT_REFERENCED_ZONE}}
`

// Other sample to exercise falsy values still being rendered as-is correctly
var falsyTemplate = `{{#FOO}}
one={{FOO}}
{{/FOO}}
{{^FOO}}
two={{FOO}}
{{/FOO}}
`

func TestRenderEmpty(t *testing.T) {
	asrt := assert.New(t)
	val := ""

	expectedCassandra := `dc=dc0
rack=rack1
`
	doCassandraTemplateTest(asrt, &val, expectedCassandra)

	expectedFalsy := "two=\n"
	doFalsyTemplateTest(asrt, &val, expectedFalsy)
}

func TestRenderFalse(t *testing.T) {
	asrt := assert.New(t)
	val := "false"

	expectedCassandra := `dc=dc0
rack=rack1
`
	doCassandraTemplateTest(asrt, &val, expectedCassandra)

	expectedFalsy := "two=false\n"
	doFalsyTemplateTest(asrt, &val, expectedFalsy)
}

func TestRenderUnset(t *testing.T) {
	asrt := assert.New(t)

	expectedCassandra := `dc=dc0
rack=rack1
`
	doCassandraTemplateTest(asrt, nil, expectedCassandra)

	expectedFalsy := "two=\n"
	doFalsyTemplateTest(asrt, nil, expectedFalsy)
}

func TestRenderTrue(t *testing.T) {
	asrt := assert.New(t)
	val := "true"

	expectedCassandra := `dc=dc0
rack=zone0
`
	doCassandraTemplateTest(asrt, &val, expectedCassandra)

	expectedFalsy := "one=true\n"
	doFalsyTemplateTest(asrt, &val, expectedFalsy)
}

func doCassandraTemplateTest(asrt *assert.Assertions, envVal *string, expected string) {
	env := make(map[string]string)
	env["CASSANDRA_LOCATION_DATA_CENTER"] = "dc0"
	env["ZONE"] = "zone0"
	if envVal != nil {
		log.Printf("Test value: %s", *envVal)
		env["PLACEMENT_REFERENCED_ZONE"] = *envVal
	} else {
		log.Printf("Test value: <nil>")
	}

	doTemplateTest(asrt, env, cassandraTemplate, expected)
}

func doFalsyTemplateTest(asrt *assert.Assertions, envVal *string, expected string) {
	env := make(map[string]string)
	if envVal != nil {
		log.Printf("Test value: %s", *envVal)
		env["FOO"] = *envVal
	} else {
		log.Printf("Test value: <nil>")
	}

	doTemplateTest(asrt, env, falsyTemplate, expected)
}

func doTemplateTest(asrt *assert.Assertions, envMap map[string]string, template string, expected string) {
	tmpfile, err := ioutil.TempFile("", "bootstrap-test")
	if err != nil {
		log.Fatal(err)
	}
	defer os.Remove(tmpfile.Name()) // clean up

	renderTemplate(template, tmpfile.Name(), envMap, "test")

	rendered, err := ioutil.ReadFile(tmpfile.Name())
	if err != nil {
		log.Fatal(err)
	}

	asrt.Equal(expected, string(rendered))
}
