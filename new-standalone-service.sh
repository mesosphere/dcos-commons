#!/bin/bash

cleanup() {
    # TODO(mohit): Maybe add a trap for SIGINT.
    rm -rf template.zip
}

info() {
    MESSAGE=$1
    echo $MESSAGE
}

debug() {
    if [[ -z "${DEBUG// }" ]]; then
        return
    fi
    MESSAGE=$1
    echo "DEBUG: $MESSAGE"
}

PROJECT_NAME=$1
PROJECT_PATH=$2

if [[ -z "${PROJECT_NAME// }" ]]; then
    echo "You must provide the name of the project as the first argument"
    echo "Usage: ./new-standalone-service.sh project-name"
    echo "Example: ./new-standalone-service.sh kafka"
    cleanup
    exit 1
fi

if [[ -z "${PROJECT_PATH// }" ]]; then
    PROJECT_PATH=$(pwd)
fi

## Download the template.zip
#TEMPLATE_DOWNLOAD_URL=https://s3-us-west-2.amazonaws.com/mohit-dev/template.zip
#
#debug "Downloading template.zip from $TEMPLATE_DOWNLOAD_URL"
#curl -o template.zip $TEMPLATE_DOWNLOAD_URL
#if [ $? -ne 0 ]; then
#    echo "Failed to download from $TEMPLATE_DOWNLOAD_URL"
#    cleanup
#    exit 1
#fi
#
#debug "Unzipping template.zip"
#unzip template.zip
#if [ $? -ne 0 ]; then
#    echo "Failed to unzip template.zip"
#    cleanup
#    exit 1
#fi
debug "Scaffolding $PROJECT_NAME from template"

#mv template/ ./$PROJECT_NAME
cp -R frameworks/template $PROJECT_PATH/$PROJECT_NAME
cp -R tools $PROJECT_PATH/$PROJECT_NAME/tools

rm -rf $PROJECT_PATH/$PROJECT_NAME/build
rm -rf $PROJECT_PATH/$PROJECT_NAME/cli/dcos-*/*.whl
rm -rf $PROJECT_PATH/$PROJECT_NAME/cli/dcos-*/dcos-*
rm -rf $PROJECT_PATH/$PROJECT_NAME/cli/python/{build,dist}
rm -rf $PROJECT_PATH/$PROJECT_NAME/build.sh
mv $PROJECT_PATH/$PROJECT_NAME/build.self.hosted.sh $PROJECT_PATH/$PROJECT_NAME/build.sh
chmox +x $PROJECT_PATH/$PROJECT_NAME/build.sh
mv $PROJECT_PATH/$PROJECT_NAME/cli/dcos-template $PROJECT_PATH/$PROJECT_NAME/cli/dcos-$PROJECT_NAME
mv $PROJECT_PATH/$PROJECT_NAME/src/main/java/com/mesosphere/sdk/template/ $PROJECT_PATH/$PROJECT_NAME/src/main/java/com/mesosphere/sdk/$PROJECT_NAME/
mv $PROJECT_PATH/$PROJECT_NAME/src/test/java/com/mesosphere/sdk/template/ $PROJECT_PATH/$PROJECT_NAME/src/test/java/com/mesosphere/sdk/$PROJECT_NAME/

if [ $? -ne 0 ]; then
    echo "Failed to create new project"
    cleanup
    exit 1
fi

UPPER_CASE_PROJECT_NAME=$(echo $PROJECT_NAME | awk '{print toupper($0)}')

find $PROJECT_PATH/$PROJECT_NAME -type f -exec sed -i.bak "s/template/$PROJECT_NAME/g; s/TEMPLATE/$UPPER_CASE_PROJECT_NAME/g; s/template/$PROJECT_NAME/g" {} \;
sed -i.bak -e '21,$ d' $PROJECT_PATH/$PROJECT_NAME/src/main/dist/svc.yml
find $PROJECT_PATH/$PROJECT_NAME -type f -name *.bak -exec rm {} \;

sed -i.bak "s/compile project(\":scheduler\")/compile \"mesosphere:scheduler:0.12.0\"/g" $PROJECT_PATH/$PROJECT_NAME/build.gradle
sed -i.bak "s/compile project(\":executor\")/compile \"mesosphere:executor:0.12.0\"/g" $PROJECT_PATH/$PROJECT_NAME/build.gradle
sed -i.bak "s/testCompile project(\":testing\")/testCompile \"mesosphere:testing:0.12.0\"/g" $PROJECT_PATH/$PROJECT_NAME/build.gradle
sed -i.bak '/distZip.dependsOn ":executor:distZip"/d' $PROJECT_PATH/$PROJECT_NAME/build.gradle
sed -i.bak '/distZip.finalizedBy copyExecutor/d' $PROJECT_PATH/$PROJECT_NAME/build.gradle

if [ $? -eq 0 ]; then
    echo "New project created successfully"
    cleanup
else
    echo "Failed to create new project"
    cleanup
    exit 1
fi
