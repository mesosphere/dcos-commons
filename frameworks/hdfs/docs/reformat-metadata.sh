#!/bin/bash

function  reformat_file_frontmatter
{
  # title:
  gsed -i'' -e 's/title:/title:/g' $1

  # navigationTitle:
  gsed -i'' -e 's/navigationTitle:/navigationTitle:/g' $1

  # menuWeight
  gsed -i'' -e 's/menuWeight:/menuWeight:/g' $1

  # excerpt:
  gsed -i'' -e 's/excerpt:/excerpt:/g' $1

  # excerpt:
  gsed -i'' -e 's/excerpt:/excerpt:/g' $1

  # 
  gsed -i'' -e "s///g" $1

  # 
  gsed -i'' -e "s///g" $1

  # 
  gsed -i'' -e "s///g" $1

  # 
  gsed -i'' -e "s///g" $1

  # featureMaturity:
  gsed -i'' -e 's/featureMaturity:/featureMaturity:/g' $1

  # featureMaturity:
  gsed -i'' -e 's/featureMaturity:/featureMaturity:/g' $1

  # excerpt:
  # If not found, add
  if ! grep "excerpt:" $1; then
    gsed -i'' -e '1s/---/---\nexcerpt:/' $1
  fi

  # menuWeight:
  # If not found, add
  if ! grep "menuWeight:" $1; then
    gsed -i'' -e '1s/---/---\nmenuWeight: 0/' $1
  fi

  # navigationTitle:
  # If not found, add
  if ! grep "navigationTitle:" $1; then
    title=`grep -o -P '(?<=title:).*' $1`
    gsed -i'' -e "1s/---/---\nnavigationTitle: $title/" $1
  fi

  # title:
  # If not found, add
  if ! grep "title:" $1; then
    gsed -i'' -e '1s/---/---\ntitle: Title/' $1
  fi

  # layout:
  # If not found, add
  if ! grep "layout:" $1; then
    gsed -i'' -e '1s/---/---\nlayout: layout.pug/' $1
  fi

}

while read p;
do
reformat_file_frontmatter $p 

done <file_list.txt
