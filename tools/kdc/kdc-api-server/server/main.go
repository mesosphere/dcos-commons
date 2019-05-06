package main

import (
	"context"
	"fmt"
	"log"
)

func main() {

	// Create a KAdmin client
	kadmin, err := CreateKAdminClient("kadmin")
	if err != nil {
		log.Fatal(err)
	}

	sout, serr, err := kadmin.exec("--help")
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println(sout)
	fmt.Println(serr)

	p, err := ParsePrincipals(`zookeeper/zookeeper-0-server.data-servicesconfluent-zookeeper-kerberos.autoip.dcos.thisdcos.directory@LOCAL
zookeeper/zookeeper-1-server.data-servicesconfluent-zookeeper-kerberos.autoip.dcos.thisdcos.directory@LOCAL
zookeeper/zookeeper-2-server.data-servicesconfluent-zookeeper-kerberos.autoip.dcos.thisdcos.directory@LOCAL
`)
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println(p)

	// Try to connect to DC/OS based on the environment settings
	// if this fails, we cannot connect to DC/OS later
	client, err := CreateDCOSClientFromEnvironment()
	if err != nil {
		log.Fatal(err)
	}

	secret, _, err := client.Secrets.GetSecret(context.TODO(), "default", "kdc-admin", nil)
	if err != nil {
		log.Fatalf("Unable to fetch the secret: %s", err)
	}

	log.Printf("Secret fetched: %+v\n", secret)
}
