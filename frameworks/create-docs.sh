#!/bin/bash
#
echo "------------------------------"
echo " Creating Docs"
echo "------------------------------"

# Get value for package name and framework variables if not supplied

packagename=$1
if [ -z "$1" ]; then echo "Enter a package name as the first argument."; exit 1; fi

frameworkname=$2
if [ -z "$2" ]; then echo "Enter a framework name as the second argument."; exit 1; fi

# make these environment variables for envsubst
export packagename
export frameworkname

# get the current directory
current_dir=$(dirname $0)

# replace the variables in the files with the values supplied and move the files to the docs directory
for i in $current_dir/$frameworkname/docs/src/* ;
do
  echo $i
  envsubst '$packagename' <$i> $i.tmp
  mv $i.tmp $current_dir/$frameworkname/docs/$(echo $i | sed 's:.*/::')
done

echo "---------------------------------------"
echo "$packagename docs complete"
echo "---------------------------------------"
