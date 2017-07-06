#!/bin/bash

# Generates docs:
# 1. Checks out a copy of the repo's gh-pages branch
# 2. Regenerates all docs into that copy
# 3a. If 'upload' argument is specified: Pushes the changes to github
# 3b. If 'exit' is specified:            Prints the output path and exits
# 3c. If no argument is specified:       Launches a local http server to view the output

DOCS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DOCS_DIR

# Inputs (relative to dcos-commons/docs/):
PAGES_PATH=${DOCS_DIR}/pages
PAGES_FRAMEWORKS_PATH_PATTERN=${DOCS_DIR}/../frameworks/*/docs/
JAVADOC_SDK_PATH_PATTERN=${DOCS_DIR}/../sdk/*/src/main/

# Output directory:
OUTPUT_BASE_DIR=dcos-commons-gh-pages
OUTPUT_DIR=${OUTPUT_BASE_DIR}/dcos-commons

# Swagger build to fetch if needed:
SWAGGER_CODEGEN_VERSION=2.2.2
SWAGGER_OUTPUT_DIR=reference/swagger-api
SWAGGER_JAR=swagger-codegen-cli-${SWAGGER_CODEGEN_VERSION}.jar
SWAGGER_URL=http://central.maven.org/maven2/io/swagger/swagger-codegen-cli/${SWAGGER_CODEGEN_VERSION}/${SWAGGER_JAR}

# Default value, override with "HTTP_PORT" envvar:
DEFAULT_HTTP_PORT=8888

# abort script at first error:
set -eu

error_msg() {
    echo "---"
    echo "Failed to generate docs: Exited early at $0:L$1"
    echo "---"
}
trap 'error_msg ${LINENO}' ERR

run_cmd() {
    echo "RUNNING COMMAND: $@"
    $@
}

UPLOAD_ENABLED=""
EXIT_ENABLED=""
if [ "${1:-}" = "upload" ]; then
    UPLOAD_ENABLED="y"
elif [ "${1:-}" = "exit" ]; then
    EXIT_ENABLED="y"
fi

if [ $UPLOAD_ENABLED ]; then
    # Fetch copy of gh-pages branch for output
    if [ -d ${OUTPUT_DIR} ]; then
        # dir exists: clear before replacing with fresh git repo
        rm -rf ${OUTPUT_DIR}
    fi
    git clone -b gh-pages --single-branch --depth 1 git@github.com:mesosphere/dcos-commons ${OUTPUT_DIR}
    rm -rf ${OUTPUT_DIR}/* # README.md is recovered later
fi

# 1. Generate common pages + framework docs using jekyll
# Workaround: '--layouts' flag seems to be ignored. cd into pages dir to generate.
pushd $PAGES_PATH
rm -rf services
mkdir -p services
for dir in $(ls -d $PAGES_FRAMEWORKS_PATH_PATTERN); do
    framework=$(echo $dir | awk -F "/" '{print $(NF-2)}')
    ln -s -v $dir services/$framework
done
# Errors? Do this!:
# sudo gem install jekyll jekyll-redirect-from
run_cmd jekyll build --destination ${DOCS_DIR}/${OUTPUT_DIR}
popd

# 2. Generate javadocs to api/ subdir
javadoc -quiet -notimestamp -package -d ${OUTPUT_DIR}/reference/api/ \
    $(find $JAVADOC_SDK_PATH_PATTERN -name *.java) 2>&1 | /dev/null || echo "Ignoring javadoc exit code. Disregard errors about /dev/null."

# 3. Generate swagger html to swagger-api/ subdir
if [ ! -f ${SWAGGER_JAR} ]; then
    curl -O ${SWAGGER_URL}
fi
run_cmd java -jar ${SWAGGER_JAR} \
    generate \
    -l html \
    -i ${SWAGGER_OUTPUT_DIR}/swagger-spec.yaml \
    -c ${SWAGGER_OUTPUT_DIR}/swagger-config.json \
    -o ${OUTPUT_DIR}/${SWAGGER_OUTPUT_DIR}/

if [ $UPLOAD_ENABLED ]; then
    # Push changes to gh-pages branch
    pushd ${OUTPUT_DIR}
    git checkout -- README.md # recover gh-pages README *after* generating docs -- otherwise it's removed via generation
    CHANGED_FILES=$(git ls-files -d -m -o --exclude-standard)
    NUM_CHANGED_FILES=$(echo $CHANGED_FILES | wc -w)
    if [ $NUM_CHANGED_FILES -eq 0 ]; then
        echo "No docs changes detected, skipping commit to gh-pages"
    else
        echo "Pushing $NUM_CHANGED_FILES changed files to gh-pages:"
        echo "--- CHANGED FILES START"
        for file in $CHANGED_FILES; do
            echo $file
        done
        echo "--- CHANGED FILES END"
        git add .
        if [ -n "${WORKSPACE+x}" ]; then
            # we're in jenkins. set contact info (not set by default)
            git config user.name "Jenkins"
            git config user.email "jenkins@example.com"
        fi
        git commit -am "Automatic update from master"
        git push origin gh-pages
    fi
    popd
elif [ $EXIT_ENABLED ]; then
    echo "-----"
    echo "Content has been generated here: file://${DOCS_DIR}/${OUTPUT_DIR}/index.html"
else
    echo "-----"
    echo "Launching test server with generated content. Use '$0 exit' to skip this."
    trap - ERR
    if [ -z "${HTTP_PORT+x}" ]; then
        HTTP_PORT=$DEFAULT_HTTP_PORT
    fi
    FAILED=""
    echo "> http://localhost:${HTTP_PORT}/dcos-commons < is now serving $DOCS_DIR/$OUTPUT_DIR"
    python $DOCS_DIR/httpd.py $DOCS_DIR/$OUTPUT_BASE_DIR $HTTP_PORT || FAILED="yes"
    if [ -n "$FAILED" ]; then
        echo "-----"
        echo "Failed to listen on HTTP_PORT=$HTTP_PORT."
        echo "Wait for that port to free up, or specify a different port like this:"
        echo "HTTP_PORT=<custom port> $0 $@"
        echo "Alternately just run '$0 exit' to skip running a test server."
    fi
fi
