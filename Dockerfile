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
    upx-ucl \
    wget \
    zip && \
    rm -rf /var/lib/apt/lists/*

# Install go 1.8.5
RUN curl -O https://storage.googleapis.com/golang/go1.8.5.linux-amd64.tar.gz && \
    tar -xf go1.8.5.linux-amd64.tar.gz && \
    mv go /usr/local
ENV PATH=$PATH:/usr/local/go/bin
RUN go version
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

# Create a tools directory:
RUN mkdir /tools
COPY test-runner.sh /tools/
