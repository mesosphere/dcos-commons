#!/bin/ksh -x


cat universe/marathon.json.mustache |grep KAFKA_OVERRIDE | awk '{FS=":"}{print $2}'>/tmp/a

>/tmp/b
while read line
do
	echo $line | sed 's/{//g' | sed 's/}//g' | sed 's/"//g' | sed 's/,//g'| awk '{print $1}' |  awk -F'.' '{print $2}' >>/tmp/b
	
done </tmp/a
while read line
do
  settingName=`echo $line | sed 's/_/./g'`
  mustacheName=`echo $line | awk '{print toupper($0)}'`	
  echo $settingName "KAFKA_"$mustacheName"={{kafka."$settingName"}}" >>mustachewalla
  echo $settingName $settingName"={{KAFKA_"$mustacheName"}}" >>settingwalla
done </tmp/b
