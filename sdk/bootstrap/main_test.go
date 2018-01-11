package main

import "io/ioutil"
import "log"
import "os"
import "testing"
import "github.com/stretchr/testify/assert"

var templateContent = `dc={{CASSANDRA_LOCATION_DATA_CENTER}}
{{#PLACEMENT_REFERENCED_ZONE}}
rack={{ZONE}}
{{/PLACEMENT_REFERENCED_ZONE}}
{{^PLACEMENT_REFERENCED_ZONE}}
rack=rack1
{{/PLACEMENT_REFERENCED_ZONE}}
`

func TestRenderEmpty(t *testing.T) {
	asrt := assert.New(t)
	expected :=`dc=dc0
rack=rack1
`
	val := ""
	doTemplateTest(asrt, &val, expected)
}

func TestRenderFalse(t *testing.T) {
	asrt := assert.New(t)
	expected :=`dc=dc0
rack=rack1
`
	val := "false"
	doTemplateTest(asrt, &val, expected)
}

func TestRenderUnset(t *testing.T) {
	asrt := assert.New(t)
	expected :=`dc=dc0
rack=rack1
`
	doTemplateTest(asrt, nil, expected)
}

func TestRenderTrue(t *testing.T) {
	asrt := assert.New(t)
	expected :=`dc=dc0
rack=zone0
`
	val := "true"
	doTemplateTest(asrt, &val, expected)
}

func doTemplateTest(asrt *assert.Assertions, envVal *string, expected string) {
	tmpfile, err := ioutil.TempFile("", "bootstrap-test")
	if err != nil {
		log.Fatal(err)
	}
	defer os.Remove(tmpfile.Name()) // clean up

	env := make(map[string]string)
	env["CASSANDRA_LOCATION_DATA_CENTER"] = "dc0"
	env["ZONE"] = "zone0"
	if envVal != nil {
		log.Printf("Test value: %s", *envVal)
		env["PLACEMENT_REFERENCED_ZONE"] = *envVal
	} else {
		log.Printf("Test value: <nil>")
	}

	renderTemplate(templateContent, tmpfile.Name(), env, "test")

	rendered, err := ioutil.ReadFile(tmpfile.Name())
	if err != nil {
		log.Fatal(err)
	}

	asrt.Equal(expected, string(rendered))
}
