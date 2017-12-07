---
title: Secrets Tutorial
---


# Secrets Tutorial


The SDK enables you to integrate DC/OS secrets using both a declarative YAML API and flexible JAVA API. In YAML, secrets are declared within the `secret:` section in a pod specification. Similarly, in JAVA API, a `SecretSpec` is added to the `PodSpec` object. 

Refer to the [Developer Guide](../developer-guide.md) for more information about the JAVA API. Refer to the [Operations Guide](../operations-guide.md) for a detailed explaination of how to use DC/OS secrets in your SDK-based service. 

In this tutorial, we will use the existing `hello-world` service to experiment with secrets. First, create a DC/OS Enterprise 1.10 cluster (at least 3 nodes is recommended). 

**Note**: Secrets integration is only supported in DC/OS Enterprise 1.10 and above.


## Create Secrets


Use the DC/OS CLI to create a secret.  You must have the Enterprise DC/OS CLI installed.

Install DC/OS Enterprise CLI:

```
$ dcos package install --cli dcos-enterprise-cli
```

Create a secret with path `hello-world/secret1`:

```
$ dcos security secrets  create -v "the value of secret1" hello-world/secret1
```

Now, create a key and add its private key to a secret with path `hello-world/secret2` and its public key to another secret with path `hello-world/secret3`.

Create key and cert:

```
$openssl genrsa -out privatekey.pem 1024
$openssl req -new -x509 -key privatekey.pem -out publickey.cer -days 1825
```

Create secrets with the values from the private and public key files:

```
$ dcos security secrets  create -f privatekey.pem  hello-world/secret2
```
```
$ dcos security secrets  create -f publickey.cer  hello-world/secret3
```


You can list secrets and view the content of a secret as follow:

```
$ dcos security secrets list  hello-world
```
```
$ dcos security secrets get hello-world/secret3
```


## Install the Service with Secrets



The `hello-world` package includes a sample YAML file for secrets. The `examples/secrets.yml` file demonstrates how secrets are declared:

```
name: {{FRAMEWORK_NAME}}
scheduler:
  principal: {{SERVICE_PRINCIPAL}}
  user: {{SERVICE_USER}}
pods:
  hello:
    image: ubuntu:14.04
    count: {{HELLO_COUNT}}
    placement: '{{{HELLO_PLACEMENT}}}'
    secrets:
      s1:
        secret: {{HELLO_SECRET1}}
        env-key: HELLO_SECRET1_ENV
        file: HELLO_SECRET1_FILE
      s2:
        secret: {{HELLO_SECRET2}}
        file: HELLO_SECRET2_FILE
    tasks:
      server:
        goal: RUNNING
        cmd: >
               env &&
               ls -la &&
               cat HELLO_SECRET*_FILE &&
        ................
  world:
    count: {{WORLD_COUNT}}
    placement: '{{{WORLD_PLACEMENT}}}'
    secrets:
      s1:
        secret: {{WORLD_SECRET1}}
        env-key: WORLD_SECRET1_ENV
      s2:
        secret: {{WORLD_SECRET2}}
        file: WORLD_SECRET2_FILE
      s3:
        secret: {{WORLD_SECRET3}}
    tasks:
      server:
        goal: RUNNING
        cmd: >
               env &&
               ls -la &&
               cat WORLD_SECRET*_FILE &&
        ................
```


The `hello` pod has two secrets. The first secret, with path `hello-world/secret1`, is exposed both as an environment variable and as a file. The second one is exposed only as a file. The value of the second secret with path `hello-world/secret2`, which is the private key, will be copied to the `HELLO_SECRET2_FILE` file located in the sandbox. 
  
The `world` pod has three secrets. The first one is exposed only as an environment variable. The second and third secrets are exposed only as files. All `server` tasks in the `world` pod will have access to the values of these three secrets, either as a file and/or as an evironment variable.  

*Note*: The secret path is the default file path if no `file` keyword is given. Therefore, the file path for the third secret is same as the secret path.


Next, install the `hello-world` package using the following "`option.json`" file:


```
$ dcos package install --options=option.json hello-world

```

```
$ cat option.json
{
      "service":{
          "spec_file" : "examples/secrets.yml"
      },
      "hello":{
          "secret1": "hello-world/secret1",
          "secret2": "hello-world/secret2"
      },
      "world":{
          "secret1": "hello-world/secret1",
          "secret2": "hello-world/secret2",
          "secret3": "hello-world/secret3"
          }
}
```

Here, we use `examples/secrets.yml` spec file. And, we also overwrite several `hello` and `world` options -  `secret1`, `secret2`, and `secret3` parameters are set to specific secret paths.  Examine `universe/config.json` for more information about the `hello-world` package configuration.



## Verify Secrets


Use the `dcos task exec` command to attach to the container that is running the task you want to examine. 

*Note*: The tasks of the `hello` pod in our example are running inside a docker image (`ubuntu:14.04`).

Run the following command to attach to the same container that is running the first task of the `hello` pod, that is `hello-0-server` (task name is `server`):

```
$ dcos task exec -it hello-0-server bash

> $ echo $HELLO_SECRET1_ENV
the value of secret1

> $ ls
HELLO_SECRET1_FILE  executor.zip	   stdout
HELLO_SECRET2_FILE  hello-container-path   stdout.logrotate.conf
containers	    stderr		   stdout.logrotate.state
executor	    stderr.logrotate.conf

$ cat HELLO_SECRET1_FILE 
the value of secret1

> $ cat HELLO_SECRET2_FILE 
..............................................
..............................................
  <KEY value created via openssl>
..............................................
..............................................
```

Similarly, you can verify the content of secrets defined for any task of the `world` pod as follows:

```
$ dcos task exec -it world-0-server bash
 
> $ echo $WORLD_SECRET1_ENV
the value of secret1 

> $ ls
WORLD_SECRET2_FILE  executor.zip  stderr.logrotate.conf  stdout.logrotate.state
containers	    hello-world   stdout		 world-container-path1
executor	    stderr	  stdout.logrotate.conf  world-container-path2

> $ cat WORLD_SECRET2_FILE  
..............................................
..............................................
  <KEY value created via openssl>
..............................................
..............................................

> $ ls hello-world/
secret3
> $ cat hello-world/secret3 
..............................................
..............................................
  <CERT value created via openssl>
..............................................
..............................................
```


The third secret for the `word` pod does not have a specific `file` keyword. Therefore, its secret path `hello-world/secret3` is also used as the file path by default. As can be seen in the output, `hello-world` directory is created and the content of the third secret is copied to the `secret3` file. 



# Update Secret Value


We can update the content of a secret as follows:

```
$ dcos security secrets update -v "This is the NEW value for secret1" hello-world/secret1
```



The secret value is securely copied from the secret store to a file and/or to an environment variable. A secret file is an in-memory file and it disappears when all tasks of the pod terminate. 

Since we updated the value of `hello-world/secret1`,  we need to restart all associated pods to copy the new value from the secret store.



Lets restart the first `hello` pod. All tasks (there is only one task called `server` in our example) in the pod will be restarted.

```
$ dcos hello-world pod restart hello-0
```


As can be seen in the output, the `hello-0-server` task is already updated after the restart.  

```
$ dcos task exec -it hello-0-server bash

> $ echo $HELLO_SECRET1_ENV                  
This is the NEW value for secret1

> $ cat HELLO_SECRET1_FILE 
This is the NEW value for secret1
```


Since we did not restart the `world` pods, their tasks still have the old value.  Run the following commands to examine the `world-0-server` task:

```
$ dcos task exec -it world-0-server bash
 
> $ echo $WORLD_SECRET1_ENV
the value of secret1 
```



Now, restart all remaining pods in order to get the new value of `hello-world/secret1`:

```
$ dcos hello-world pod list
[
  "hello-0",
  "world-0",
  "world-1"
]

$ dcos hello-world pod restart world-0
$ dcos hello-world pod restart world-1
```


