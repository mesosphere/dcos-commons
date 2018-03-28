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

# Create a build-tool directory:
RUN mkdir /build-tools
ENV PATH /build-tools:$PATH

COPY tools/distribution/init /build-tools/
COPY tools/ci/* /build-tools/

# Create a folder to store the distributed artefacts
RUN mkdir /dcos-commons-dist

ENV DCOS_COMMONS_DIST_ROOT /dcos-commons-dist

COPY tools/distribution/* ${DCOS_COMMONS_DIST_ROOT}/

COPY test.sh ${DCOS_COMMONS_DIST_ROOT}/
COPY TESTING.md ${DCOS_COMMONS_DIST_ROOT}/

COPY conftest.py ${DCOS_COMMONS_DIST_ROOT}/

COPY testing ${DCOS_COMMONS_DIST_ROOT}/testing
COPY tools ${DCOS_COMMONS_DIST_ROOT}/tools

COPY build.gradle ${DCOS_COMMONS_DIST_ROOT}/build.gradle
RUN grep -oE "version = '.*?'" ${DCOS_COMMONS_DIST_ROOT}/build.gradle | sed 's/version = //' > ${DCOS_COMMONS_DIST_ROOT}/.version
