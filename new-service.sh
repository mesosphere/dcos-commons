#!/bin/bash

if [ $# -lt 1 ]; then
    echo "You must provide the name of the project as the first argument"
    echo "Usage: ./new-service.sh <dir-path>/<project-name>"
    echo "Example: ./new-service.sh frameworks/kafka"
    exit 1
elif [ -d $1 ]; then
    echo "A project with the given name '$1' already exists. Choose a different name"
    exit 1
fi

PROJECT_NAME=$(basename $1)
PROJECT_PATH=$(dirname $1)

mkdir -p $PROJECT_PATH

cp -R frameworks/helloworld $1
rm -rf $1/build
rm -rf $1/cli/dcos-*/*.whl
rm -rf $1/cli/dcos-*/dcos-*
mv $1/cli/dcos-hello-world $1/cli/dcos-$PROJECT_NAME
mv $1/src/test/java/com/mesosphere/sdk/helloworld/ $1/src/test/java/com/mesosphere/sdk/$PROJECT_NAME/

if [ ! -d $1 ]; then
    echo "Failed to create new project"
    exit 1
fi

UPPER_CASE_PROJECT_NAME=$(echo $PROJECT_NAME | awk '{print toupper($0)}')

find $1 -type f -exec sed -i.bak "s/hello[-]*world/$PROJECT_NAME/g; s/HELLO/$UPPER_CASE_PROJECT_NAME/g; s/hello/$PROJECT_NAME/g" {} \;
sed -i.bak -e '21,$ d' $1/src/main/dist/svc.yml
sed -i.bak -e '23,26 d' $1/universe/marathon.json.mustache
find $1 -type f -name *.bak -exec rm {} \;

if [ $? -eq 0 ]; then
    echo "" >> settings.gradle
    echo "include '$1'" >> settings.gradle
    echo "project(\":$1\").name = \"$PROJECT_NAME\"" >> settings.gradle
    echo "New project created successfully"
else
    echo "Failed to create new project"
    exit 1
fi
