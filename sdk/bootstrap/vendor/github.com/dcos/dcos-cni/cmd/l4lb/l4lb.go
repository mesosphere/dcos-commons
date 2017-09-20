// Copyright 2015 CNI authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"encoding/json"
	"fmt"
	"log"
	"runtime"

	"github.com/dcos/dcos-cni/pkg/l4lb"
	"github.com/dcos/dcos-cni/pkg/minuteman"
	"github.com/dcos/dcos-cni/pkg/spartan"

	"github.com/containernetworking/cni/pkg/invoke"
	"github.com/containernetworking/cni/pkg/ip"
	"github.com/containernetworking/cni/pkg/skel"
	"github.com/containernetworking/cni/pkg/version"
)

// By default Spartan and Minuteman are specified to be enabled.

func init() {
	// This ensures that main runs only on main thread (thread group leader).
	// Since namespace ops (unshare, setns) are done for a single thread, we
	// must ensure that the goroutine does not jump from OS thread to thread
	runtime.LockOSThread()
}

func cmdAdd(args *skel.CmdArgs) error {
	conf := l4lb.NewNetConf()

	if err := json.Unmarshal(args.StdinData, conf); err != nil {
		return fmt.Errorf("failed to load netconf: %s", err)
	}

	if err := ip.EnableIP4Forward(); err != nil {
		return fmt.Errorf("failed to enable forwarding: %s", err)
	}

	delegateConf, delegatePlugin, err := conf.SetupDelegateConf()
	if err != nil {
		return fmt.Errorf("failed to retrieve delegate configuration: %s", err)
	}

	delegateResult, err := invoke.DelegateAdd(delegatePlugin, delegateConf)
	if err != nil {
		return fmt.Errorf("failed to invoke delegate plugin %s: %s", delegatePlugin, err)
	}

	if conf.Spartan.Enable {
		log.Println("Spartan enabled:", conf.Spartan)
		// Install the spartan network.
		err := spartan.CniAdd(args)
		if err != nil {
			return fmt.Errorf("failed: %s", err)
		}

		//TODO(asridharan): We probably need to update the DNS result to
		//make sure that we override the DNS resolution with the spartan
		//network, since the operator has explicitly requested to use the
		//spartan network.
	}

	// Check if minuteman needs to be enabled for this container.
	log.Println("Minuteman enabled:", conf.Minuteman.Enable)

	if conf.Minuteman.Enable {
		log.Println("Asking plugin to register container netns for minuteman")
		minutemanArgs := *args
		minutemanArgs.StdinData, err = json.Marshal(conf.Minuteman)
		if err != nil {
			return fmt.Errorf("failed to marshal the minuteman configuration into STDIN for the minuteman plugin")
		}

		err = minuteman.CniAdd(&minutemanArgs)
		if err != nil {
			return fmt.Errorf("failed to register container:%s with minuteman: %s", args.ContainerID, err)
		}
	}

	// We always return the result from the delegate plugin and not from
	// this plugin.
	return delegateResult.Print()
}

func cmdDel(args *skel.CmdArgs) error {
	conf := l4lb.NewNetConf()

	if err := json.Unmarshal(args.StdinData, conf); err != nil {
		return fmt.Errorf("failed to load netconf: %s", err)
	}

	if conf.Spartan.Enable {
		err := spartan.CniDel(args)
		if err != nil {
			return fmt.Errorf("failed to invoke the spartan plugin with CNI_DEL")
		}
	}

	if conf.Minuteman.Enable {
		var err error
		minutemanArgs := *args
		// Check if minuteman entries need to be removed from this container.
		minutemanArgs.StdinData, err = json.Marshal(conf.Minuteman)
		if err != nil {
			return fmt.Errorf("failed to marshal the minuteman configuration into STDIN for the minuteman plugin")
		}

		err = minuteman.CniDel(&minutemanArgs)
		if err != nil {
			return fmt.Errorf("Unable to register container:%s with minuteman: %s", args.ContainerID, err)
		}
	}

	// Invoke the delegate plugin.
	delegateConf, delegatePlugin, err := conf.SetupDelegateConf()
	if err != nil {
		return fmt.Errorf("failed to retrieve delegate configuration: %s", err)
	}

	err = invoke.DelegateDel(delegatePlugin, delegateConf)
	if err != nil {
		return fmt.Errorf("failed to invoke delegate plugin %s: %s", delegatePlugin, err)
	}

	return nil
}

func main() {
	skel.PluginMain(cmdAdd, cmdDel, version.All)
}
