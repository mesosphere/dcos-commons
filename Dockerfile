FROM ubuntu:18.04

ENV GO_VERSION=1.10.2

# Install JDK via PPA: https://github.com/franzwong/til/blob/master/java/silent-install-oracle-jdk8-ubuntu.md
RUN apt-get update && \
    apt-get install -y python3-software-properties software-properties-common && \
    add-apt-repository -y ppa:webupd8team/java && \
    apt-get update && \
    echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections && \
    apt-get install -y \
    curl \
    git \
    jq \
    libssl-dev \
    oracle-java8-installer \
    python-pip \
    python3 \
    python3-dev \
    python3-pip \
    rsync \
    tox \
    software-properties-common \
    upx-ucl \
    wget \
    zip && \
    rm -rf /var/lib/apt/lists/* && \
    java -version && \
    curl -O https://storage.googleapis.com/golang/go${GO_VERSION}.linux-amd64.tar.gz && \
    tar -xf go${GO_VERSION}.linux-amd64.tar.gz && \
    mv go /usr/local && \
    rm -f go${GO_VERSION}.linux-amd64.tar.gz

ENV PATH=$PATH:/usr/local/go/bin
RUN go version

# Get DC/OS CLI
RUN curl -O https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.12/dcos && \
    chmod +x dcos && \
    mv dcos /usr/local/bin && \
    dcos --version

# AWS CLI for uploading build artifacts
RUN pip3 install awscli
# Install the lint+testing dependencies
COPY test_requirements.txt test_requirements.txt
RUN pip3 install --upgrade -r test_requirements.txt

# Deprecated libraries used by external repos.
# As of PR#2616, these libraries aren't used by dcos-commons anymore.
# However external repos using this image currently still need them.
# Remove these once external repos aren't depending on them anymore.
RUN pip3 install git+https://github.com/dcos/dcos-cli.git@f1fd38c9e72e1d521cf8120fe4948a789fb40cbc && \
    pip3 install git+https://github.com/dcos/dcos-cli.git@f1fd38c9e72e1d521cf8120fe4948a789fb40cbc#egg=dcoscli&subdirectory=cli && \
    pip3 install dcos-shakedown==1.4.12

# dcos-cli and lint tooling require this to output cleanly
ENV LC_ALL=C.UTF-8 LANG=C.UTF-8
# use an arbitrary path for temporary build artifacts
ENV GOPATH=/go-tmp
# make a dir for holding the SSH key in tests
RUN mkdir /root/.ssh

# Copy all of the repo into the image, then run some build/lint commands against the copy to heat up caches. Then delete the copy.
RUN mkdir /tmp/repo/
COPY / /tmp/repo/
# gradlew: Heat up jar cache. pre-commit: Heat up lint tooling cache.
RUN cd /tmp/repo/ && \
    ./gradlew testClasses && \
    pre-commit install-hooks && \
    cd / && \
    rm -rf /tmp/repo/

# Create a build-tool directory:
RUN mkdir /build-tools
ENV PATH=/build-tools:$PATH

COPY tools/distribution/init /build-tools/
COPY tools/ci/test_runner.sh /build-tools/
COPY tools/ci/launch_cluster.sh /build-tools/

# Create a folder to store the distributed artefacts
RUN mkdir /dcos-commons-dist
ENV DCOS_COMMONS_DIST_ROOT /dcos-commons-dist

COPY tools/distribution/* ${DCOS_COMMONS_DIST_ROOT}/

COPY test.sh ${DCOS_COMMONS_DIST_ROOT}/
COPY TESTING.md ${DCOS_COMMONS_DIST_ROOT}/

COPY conftest.py ${DCOS_COMMONS_DIST_ROOT}/

COPY testing ${DCOS_COMMONS_DIST_ROOT}/testing
COPY tools ${DCOS_COMMONS_DIST_ROOT}/tools
COPY .pre-commit-config.yaml ${DCOS_COMMONS_DIST_ROOT}/

COPY build.gradle ${DCOS_COMMONS_DIST_ROOT}/build.gradle
RUN grep -oE "version = '.*?'" ${DCOS_COMMONS_DIST_ROOT}/build.gradle | sed 's/version = //' > ${DCOS_COMMONS_DIST_ROOT}/.version
