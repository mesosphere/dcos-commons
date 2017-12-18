---
post_title: SSL Auth
menu_order: 23
enterprise: 'yes'
---

## How to connect an authenticated client

### Create a DC/OS CA signed certificate (Note: This can be replaced with some enterprise CLI calls)

#### Manually
1. Generate a private key
```bash
$ openssl genrsa -out priv.key 2048
```
2. Generate a CSR. Note: The `principal` of the TLS cert is the values of the CSR (e.g. CN=host1.example.com,OU=,O=Confluent,L=London,ST=London,C=GB)
```bash
$ openssl req -new -sha256 -key priv.key -out request.csr
```
3. Make a request to the DC/OS CA
```bash
#Request
$ curl -X POST \
  -H "Authorization: token=$(dcos config show core.dcos_acs_token)" \
  http://bwood-8zp-elasticl-1tcip3im8ushp-1069834978.us-west-2.elb.amazonaws.com/ca/api/v2/sign \
  -d '{"certificate_request": "<json-encoded-value-of-request.csr>"}'

# Response
{"success":true,"result":{"certificate":"<json-encoded-public-key"},"errors":[],"messages":[]}
```
4. Decode the JSON and save the generated certificate to `pub.crt`

#### Using the Enterprise CLI
1. Install the DC/OS Enterprise CLI
```bash
$ dcos package install dcos-enterprise-cli --yes
```
2. Create a signed certificate
```bash
$ dcos security cluster ca newcert --cn test --host test
certificate: '<Cert>'
certificate_request: '<CSR>'
private_key: '<Private Key> '

# Note: right now these output with extra newlines :(
```

### Install Kafka with TLS Auth Enabled
1. Create a DC/OS Service Account
```bash
# Install the enterprise CLI
$ dcos package install dcos-enterprise-cli --yes

# Create a service account
$ dcos security org service-accounts keypair priv.pem pub.pem
$ dcos security org service-accounts create -p pub.pem -d "testing" service-acct

# Set the service account secret
$ dcos security secrets create-sa-secret priv.pem service-acct secret

# Grant it superuser permissions (required for TLS currently)
$ dcos security org users grant service-acct dcos:superuser full
```
2. Install the Kafka package
```bash
$ vi /tmp/options.json
{
  "service": {
    "name": "kafka",
    "service_account": "service-acct",
    "service_account_secret": "secret",
    "security": {
      "transport_encryption": {
        "enabled": true
      },
      "ssl_authentication": {
        "enabled": true
      }
    }
  }
}

$ dcos package install beta-kafka --options=/tmp/options.json
```

### Connect a client
0. Get the VIP from the endpoints API
```bash
$ dcos beta-kafka --name=kafka endpoints broker-tls

{
  "address": [
    "10.0.1.168:1025",
    "10.0.3.145:1025",
    "10.0.2.176:1025"
  ],
  "dns": [
    "kafka-0-broker.kafka.autoip.dcos.thisdcos.directory:1025",
    "kafka-1-broker.kafka.autoip.dcos.thisdcos.directory:1025",
    "kafka-2-broker.kafka.autoip.dcos.thisdcos.directory:1025"
  ],
  "vip": "broker-tls.kafka.l4lb.thisdcos.directory:9093"
}
```
1. SSH to the leader
```bash
$ dcos node ssh --master-proxy --leader
```
2. Copy over the content of pub.crt and priv.key via copy/paste
3. Copy the ca bundle to the working directory
```bash
cp /run/dcos/pki/CA/ca-bundle.crt .
```
4. Convert the pub/priv keypair to a PKCS12 key
```bash
$ openssl pkcs12 -export -in pub.crt -inkey priv.key \
               -out keypair.p12 -name keypair \
               -CAfile ca-bundle.crt -caname root

# When prompted, set the password to "export"
```
5. Run the kafka docker image
```bash
$ docker run --rm -ti \
    -v /home/core:/tmp \
    -w /opt/kafka/bin \
    wurstmeister/kafka \
    bash
```
6. Create the keystore
```bash
$ keytool -importkeystore \
        -deststorepass changeit -destkeypass changeit -destkeystore /tmp/keystore.jks \
        -srckeystore /tmp/keypair.p12 -srcstoretype PKCS12 -srcstorepass export \
        -alias keypair
```
7. Create the truststore
```bash
$ keytool -import \
  -trustcacerts \
  -alias root \
  -file /tmp/ca-bundle.crt \
  -storepass changeit \
  -keystore /tmp/truststore.jks

# You'll be prompted to trust a cert. Just say yes :)
```
8. Write the client config
```bash
$ cat >/tmp/client.properties << EOL
security.protocol = SSL
ssl.truststore.location = /tmp/truststore.jks
ssl.truststore.password = changeit
ssl.keystore.location = /tmp/keystore.jks
ssl.keystore.password = changeit
EOL
```
9. Open a second terminal. SSH to the master. Start the docker container with the same command in step 5.
10. Start the consumer in one terminal, and the producer in another
```bash
# Terminal session 1
$ ./kafka-console-producer.sh \
  --broker-list broker-tls.kafka.l4lb.thisdcos.directory:9093 \
  --topic test \
  --producer.config /tmp/client.properties

# Terminal session 2
$ ./kafka-console-consumer.sh \
  --bootstrap-server broker-tls.kafka.l4lb.thisdcos.directory:9093 \
  --topic test \
  --consumer.config /tmp/client.properties
```
11. Send messages to your heart's content. Note: Starting the producer will create the topic test, but it will take the consumer a few moments to be happy (a leader for the test topic must be elected).


#### References
- https://docs.mesosphere.com/1.10/cli/enterprise-cli/#ent-cli-install
- https://docs.mesosphere.com/1.10/security/service-auth/custom-service-auth/
- https://docs.hortonworks.com/HDPDocuments/HDP2/HDP-2.6.2/bk_security/content/ch_wire-kafka.html
- https://www.confluent.io/blog/apache-kafka-security-authorization-authentication-encryption/
- https://stackoverflow.com/questions/906402/how-to-import-an-existing-x509-certificate-and-private-key-in-java-keystore-to-u
