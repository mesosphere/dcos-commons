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

debug "Scaffolding $PROJECT_NAME from template"

cp -R frameworks/template $PROJECT_PATH/$PROJECT_NAME
cp -R tools $PROJECT_PATH/$PROJECT_NAME/tools

rm -rf $PROJECT_PATH/$PROJECT_NAME/build
rm -rf $PROJECT_PATH/$PROJECT_NAME/cli/dcos-*/*.whl
rm -rf $PROJECT_PATH/$PROJECT_NAME/cli/dcos-*/dcos-*
rm -rf $PROJECT_PATH/$PROJECT_NAME/cli/python/{build,dist}
rm -rf $PROJECT_PATH/$PROJECT_NAME/build.sh
mv $PROJECT_PATH/$PROJECT_NAME/build.self.hosted.sh $PROJECT_PATH/$PROJECT_NAME/build.sh
chmod +x $PROJECT_PATH/$PROJECT_NAME/build.sh
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

GRADLE_EXISTS="yes"
command -v gradle >/dev/null 2>&1 || { echo >&2 "I require gradle but it's not installed. Please install gradle and then run 'gradle wrapper --gradle-version 3.4' inside $PROJECT_PATH/$PROJECT_NAME"; GRADLE_EXISTS="no"; }

if [ "$GRADLE_EXISTS" == "yes" ]; then
    pushd $PROJECT_PATH/$PROJECT_NAME
    gradle wrapper --gradle-version 3.4
    popd
fi

if [ $? -eq 0 ]; then
    echo "New project created successfully"
    cleanup
else
    echo "Failed to create new project"
    cleanup
    exit 1
fi
