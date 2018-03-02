With transport encryption enabled, DC/OS {{ include.data.techName }} will automatically deploy all nodes with the correct configuration to encrypt communication via SSL. The nodes will communicate securely between themselves using SSL.{{ #include.data.plaintext }} Optionally, plaintext communication can be left open to clients.{{ /include.data.plaintext }}

The service uses the [DC/OS CA](https://docs.mesosphere.com/latest/security/ent/tls-ssl/) to generate the SSL artifacts that it uses to secure the service. Any client that trusts the DC/OS CA will consider the service's certificates valid.
