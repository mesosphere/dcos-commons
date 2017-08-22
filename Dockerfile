FROM ubuntu:16.04
RUN apt-get update && apt-get install -y \
    git \
    curl \
    jq \
    default-jdk \
    python-pip \
    python3 \
    python3-dev \
    python3-pip \
    tox \
    software-properties-common \
    python-software-properties \
    libssl-dev \
    wget \
    zip && \
    # install go 1.7
    add-apt-repository -y ppa:longsleep/golang-backports && \
    apt-get update && apt-get install -y golang-go && \
    rm -rf /var/lib/apt/lists/*
# AWS CLI for uploading build artifacts
RUN pip install awscli
# Install the testing dependencies
COPY test_requirements.txt test_requirements.txt
RUN pip3 install -r test_requirements.txt
# shakedown and dcos-cli require this to output cleanly
ENV LC_ALL=C.UTF-8 LANG=C.UTF-8
# use an arbitrary path for temporary build artifacts
ENV GOPATH=/go-tmp
# make a dir for holding the SSH key in tests
RUN mkdir /root/.ssh
