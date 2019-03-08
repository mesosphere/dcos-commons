# See Dockerfile.base for instructions on how to update this base image.
FROM mesosphere/dcos-commons-base:latest@sha256:2aaf8f7398bd6f2fc02c6388e566aafd3a2ad05c0fb8162702aab7aecd263957

ENV GO_VERSION=1.10.2
ENV PATH=$PATH:/usr/local/go/bin

RUN curl -O https://storage.googleapis.com/golang/go${GO_VERSION}.linux-amd64.tar.gz && \
    tar -zxf go${GO_VERSION}.linux-amd64.tar.gz && \
    mv go /usr/local && \
    rm -f go${GO_VERSION}.linux-amd64.tar.gz && \
    go version

# Install the lint+testing dependencies and AWS CLI for uploading build artifacts
COPY frozen_requirements.txt frozen_requirements.txt
RUN pip3 install --upgrade -r frozen_requirements.txt
COPY tools/validate_pip_freeze.py /usr/local/bin
RUN validate_pip_freeze.py frozen_requirements.txt

# Get DC/OS CLI
COPY dep-snapshots/dcos /usr/local/bin

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
    git init && \
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
