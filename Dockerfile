FROM ubuntu:16.04
RUN apt-get update && apt-get install -y \
    python3 \
    git \
    curl \
    jq \
    default-jdk \
    python-pip \
    python3-dev \
    python3-pip \
    software-properties-common \
    python-software-properties \
    libssl-dev \
    wget \
    zip
RUN add-apt-repository -y ppa:longsleep/golang-backports && apt-get update && apt-get install -y golang-go
RUN pip install awscli
RUN pip3 install requests==2.10.0 dcoscli==0.4.16 dcos==0.4.16 dcos-shakedown teamcity-messages
RUN wget https://downloads.dcos.io/dcos-test-utils/bin/linux/dcos-launch -O /usr/bin/dcos-launch
RUN chmod +x /usr/bin/dcos-launch
# shakedown and dcos-cli require this
ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8
ENV GOPATH=/foo
