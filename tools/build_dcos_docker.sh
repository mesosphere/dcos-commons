#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

if [ "$(basename $(pwd))" != "dcos-docker" ]; then
    if [ ! -d "dcos-docker" ]; then
        git clone https://github.com/dcos/dcos-docker.git
    fi
    cd dcos-docker/
fi
VAGRANT_DIR=$(pwd)

if [ ! -f "dcos_generate_config.sh" ]; then
    curl -O https://downloads.dcos.io/dcos/stable/dcos_generate_config.sh
fi

echo "### Destroying pre-existing VM, if any"
vagrant destroy

echo "### Building VM"
vagrant/resize-disk.sh 20480

read -r -d '' COMMAND <<- EOF
  echo ### Launch DC/OS Cluster &&
  cd /vagrant/ &&
  rm -f dcos-genconf.* &&
  make PUBLIC_AGENTS=0 AGENTS=3 &&
  echo ### Install Git/Golang &&
  yes | sudo yum install git golang &&
  echo ### Install pip &&
  curl https://bootstrap.pypa.io/get-pip.py | sudo python &&
  echo ### Install DC/OS CLI &&
  curl https://downloads.dcos.io/binaries/cli/linux/x86-64/latest/dcos > /vagrant/dcos &&
  chmod +x /vagrant/dcos &&
  echo ### Install JDK &&
  if [ ! -f jdk-8u112-linux-x64.tar.gz ]; then
    curl -O https://downloads.mesosphere.com/java/jdk-8u112-linux-x64.tar.gz
  fi &&
  cd /opt && rm -rf jdk*/ &&
  sudo tar xzf /vagrant/jdk-8u112-linux-x64.tar.gz &&
  echo ### Configure env &&
  echo 'export GOPATH=/vagrant/go' >> ~/.bash_profile &&
  echo 'export JAVA_HOME=\$(echo /opt/jdk*)' >> ~/.bash_profile &&
  echo 'export PATH=/vagrant:\$JAVA_HOME/bin:\$PATH' >> ~/.bash_profile &&
  echo 'eval \$(ssh-agent -s)' >> ~/.bash_profile &&
  echo 'ssh-add /vagrant/genconf/ssh_key' >> ~/.bash_profile &&
  echo 'cd /vagrant/' >> ~/.bash_profile &&
  . ~/.bash_profile &&
  echo ### Check Cluster is up &&
  make postflight &&
  echo ### Point CLI to Cluster &&
  dcos config set core.dcos_url http://172.17.0.2
EOF

echo "### Launching cluster and installing tools in VM"
vagrant ssh -c "$COMMAND"

echo "### Configuring routing from host to DC/OS Master"
# "uname -a" samples:
# OSX: Darwin mesospheres-MacBook-Pro-2.local 15.6.0 Darwin Kernel Version 15.6.0: Mon Aug 29 20:21:34 PDT 2016; root:xnu-3248.60.11~1/RELEASE_X86_64 x86_64
# Linux: Linux augustao 4.2.0-42-generic #49-Ubuntu SMP Tue Jun 28 21:26:26 UTC 2016 x86_64 x86_64 x86_64 GNU/Linux
KERNEL=$(uname -a | awk '{print $1}')
if [ "$KERNEL" = "Linux" ]; then
    sudo ip route replace 172.17.0.0/16 via 192.168.65.50
elif [ "$KERNEL" = "Darwin" ]; then
    sudo route -nv add -net 172.17.0.0/16 192.168.65.50
else
    echo "Unknown kernel for route configuration: $KERNEL (from '$(uname -a)')"
fi

echo "### Fetching/building SDK"

read -r -d '' COMMAND <<- EOF
  git clone https://github.com/mesosphere/dcos-commons.git &&
  cd dcos-commons/ &&
  ./gradlew check
EOF

echo "----"
echo "SDK Path:        ${VAGRANT_DIR}/dcos-commons"
echo "Dashboard URL:   http://172.17.0.2"
echo "Log into VM:     cd ${VAGRANT_DIR} && vagrant ssh"
echo "Build SDK in VM: cd /vagrant/dcos-commons && ./gradlew check"
echo "---"
