package commands

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type PodsTestSuite struct {
	suite.Suite
}

func TestPodsTestSuite(t *testing.T) {
	suite.Run(t, new(PodsTestSuite))
}

func (suite *PodsTestSuite) TestStatusTreeServiceHelloWorld() {
	inputJSON := `{
  "service": "hello-world",
  "pods": [
    {
      "instances": [{
        "name": "hello-0",
        "tasks": [{
          "name": "hello-0-server",
          "id": "hello-0-server__35915c74-b2ad-48b3-ae56-74cab66e8654",
          "status": "RUNNING"
        }]
      }],
      "name": "hello"
    },
    {
      "instances": [
        {
          "name": "world-0",
          "tasks": [{
            "name": "world-0-server",
            "id": "world-0-server__acbac042-a456-4fb0-b75e-ea3bcd235261",
            "status": "PAUSED"
          }]
        },
        {
          "name": "world-1",
          "tasks": [{
            "name": "world-1-server",
            "id": "world-1-server__3791c51d-de84-47de-9c03-9245541085cc",
            "status": "RUNNING"
          }]
        }
      ],
      "name": "world"
    }
  ]
}`

	expectedOutput := `hello-world
├─ hello
│  └─ hello-0
│     └─ hello-0-server (RUNNING)
└─ world
   ├─ world-0
   │  └─ world-0-server (PAUSED)
   └─ world-1
      └─ world-1-server (RUNNING)`

	result := toServiceTree([]byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PodsTestSuite) TestStatusTreeServiceHelloWorldMoreTasks() {
	inputJSON := `{
  "service": "hello-world",
  "pods": [
    {
      "instances": [{
        "name": "hello-0",
        "tasks": [{
          "name": "hello-0-server",
          "id": "hello-0-server__35915c74-b2ad-48b3-ae56-74cab66e8654",
          "status": "RUNNING"
        },
        {
          "name": "hello-0-sidecar",
          "id": "hello-0-sidecar__35915c74-b2ad-48b3-ae56-74cab66e8654",
          "status": "RUNNING"
        }]
      }],
      "name": "hello"
    },
    {
      "instances": [
        {
          "name": "world-0",
          "tasks": [{
            "name": "world-0-server",
            "id": "world-0-server__acbac042-a456-4fb0-b75e-ea3bcd235261",
            "status": "PAUSING"
          },
          {
            "name": "world-0-sidecar",
            "id": "world-0-sidecar__acbac042-a456-4fb0-b75e-ea3bcd235261",
            "status": "PAUSED"
          }]
        },
        {
          "name": "world-1",
          "tasks": [{
            "name": "world-1-server",
            "id": "world-1-server__3791c51d-de84-47de-9c03-9245541085cc",
            "status": "RUNNING"
          },
          {
            "name": "world-1-sidecar",
            "id": "world-1-sidecar__3791c51d-de84-47de-9c03-9245541085cc",
            "status": "ONCE"
          }]
        }
      ],
      "name": "world"
    }
  ]
}`

	expectedOutput := `hello-world
├─ hello
│  └─ hello-0
│     ├─ hello-0-server (RUNNING)
│     └─ hello-0-sidecar (RUNNING)
└─ world
   ├─ world-0
   │  ├─ world-0-server (PAUSING)
   │  └─ world-0-sidecar (PAUSED)
   └─ world-1
      ├─ world-1-server (RUNNING)
      └─ world-1-sidecar (ONCE)`

	result := toServiceTree([]byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PodsTestSuite) TestStatusTreeServiceSingleTask() {
	inputJSON := `{
  "service": "hello-world",
  "pods": [
    {
      "instances": [{
        "name": "hello-0",
        "tasks": [{
          "name": "hello-0-server",
          "id": "hello-0-server__35915c74-b2ad-48b3-ae56-74cab66e8654",
          "status": "KILLED"
        }]
      }],
      "name": "hello"
    }
  ]
}`

	expectedOutput := `hello-world
└─ hello
   └─ hello-0
      └─ hello-0-server (KILLED)`

	result := toServiceTree([]byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PodsTestSuite) TestStatusTreeServiceNoTasks() {
	inputJSON := `{
  "service": "hello-world",
  "pods": [
  ]
}`

	expectedOutput := `hello-world`

	result := toServiceTree([]byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PodsTestSuite) TestStatusTreePodTwoTasks() {
	inputJSON := `{
  "name": "world-0",
  "tasks": [{
    "name": "world-0-server",
    "id": "world-0-server__acbac042-a456-4fb0-b75e-ea3bcd235261",
    "status": "PAUSING"
  },
  {
    "name": "world-0-sidecar",
    "id": "world-0-sidecar__acbac042-a456-4fb0-b75e-ea3bcd235261",
    "status": "PAUSED"
  }]
}`

	expectedOutput := `world-0
├─ world-0-server (PAUSING)
└─ world-0-sidecar (PAUSED)`

	result := toSinglePodTree([]byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}

func (suite *PodsTestSuite) TestStatusTreePodOneTask() {
	inputJSON := `{
  "name": "world-0",
  "tasks": [{
    "name": "world-0-server",
    "id": "world-0-server__acbac042-a456-4fb0-b75e-ea3bcd235261",
    "status": "PAUSED"
  }]
}`

	expectedOutput := `world-0
└─ world-0-server (PAUSED)`

	result := toSinglePodTree([]byte(inputJSON))
	assert.Equal(suite.T(), expectedOutput, result)
}
