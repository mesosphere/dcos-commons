#!/bin/sh

# Publishes docs changes to the gh-pages branch.

# abort script at first error:
set -e

error_msg() {
    echo "---"
    echo "Failed to generate docs: Exited early at $0:L$1"
    echo "---"
}
trap 'error_msg ${LINENO}' ERR

DOCS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DOCS_DIR

DEST_DIR_NAME=dcos-commons-gh-pages
if [ -d $DEST_DIR_NAME ]; then
    # clear any prior state...
    rm -rf $DEST_DIR_NAME
fi
git clone -b gh-pages git@github.com:mesosphere/dcos-commons $DEST_DIR_NAME

# 1. Generate jekyll site and copy images
pushd $DEST_DIR_NAME
jekyll new $DEST_DIR_NAME
cp -r *.png $DEST_DIR_NAME

# 2. Generate javadocs in api/ subdir
rm -rf $DEST_DIR_NAME/api/
javadoc -d $DEST_DIR_NAME/api/ $(find $DOCS_DIR/.. -name *.java)

# 3. Generate swagger html in swagger-api/ subdir
swagger-something sdk-api-swagger-src.yaml $DEST_DIR_NAME/swagger-api/

git commit -am "Automatic update from master branch"
git push origin gh-pages
