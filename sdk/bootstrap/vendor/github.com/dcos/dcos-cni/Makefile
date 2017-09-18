# Disable make's implicit rules, which are not useful for golang, and
# slow down the build considerably.

VPATH=bin:plugins/l4lb:pkg/spartan

# Default go OS to linux
export GOOS?=linux

# Set $GOPATH to a local directory so that we are not influenced by
# the hierarchical structure of an existing $GOPATH directory.
export GOPATH=$(shell pwd)/gopath

ifeq ($(VERBOSE),1)
	TEST_VERBOSE=-ginkgo.v
endif

PKGS=mesos \
     l4lb\
     minuteman\
     spartan\

#dcos-l4lb
L4LB=github.com/dcos/dcos-cni/cmd/l4lb
L4LB_SRC=$(wildcard cmd/l4lb/*.go)\
	 $(wildcard pkg/l4lb/*.go)\
	 $(wildcard pkg/spartan/*.go)\
	 $(wildcard pkg/l4lb/*.go)

L4LB_TEST_SRC=$(wildcard cmd/l4lbl/*_tests.go)

MESOS=github.com/dcos/dcos-cni/pkg/mesos
MESOS_SRC= $(wildcard pkg/mesos/*.go)
MESOS_TEST_SRC=$(wildcard pkg/mesos/*_tests.go)

PLUGINS=dcos-l4lb
TESTS=dcos-l4lb-test \
      mesos-test

.PHONY: all plugin clean

default: all

clean:
	rm -rf vendor/ bin/ gopath/

gopath:
	mkdir -p gopath/src/github.com/dcos
	ln -s `pwd` gopath/src/github.com/dcos/dcos-cni

# To update upstream dependencies, delete the glide.lock file first.
# Use this to populate the vendor directory after checking out the
# repository.
vendor: glide.yaml
	echo $(GOPATH)
	glide install -strip-vendor

dcos-l4lb:$(L4LB_SRC)
	echo "GOPATH:" $(GOPATH)
	mkdir -p `pwd`/bin
	go build -v -o `pwd`/bin/$@ $(L4LB)

$(PKGS): %: $(wildcard pkg/%/*.go)
	go build -v github.com/dcos/dcos-cni/pkg/$@

plugin: gopath vendor $(PKGS) $(PLUGINS)

dcos-l4lb-test:$(L4LB_TEST_SRC)
	echo "GOPATH:" $(GOPATH)
	go test $(L4LB) -test.v $(TEST_VERBOSE)

mesos-test:$(MESOS_TEST_SRC) $(MESOS_SRC)
	echo "GOPATH:" $(GOPATH)
	go test $(MESOS) -test.v $(TEST_VERBOSE)

tests: $(TESTS)

all: plugin

