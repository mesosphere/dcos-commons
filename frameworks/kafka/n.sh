#!/bin/ksh 

>notFoundinSetting
while read line 
do
	key=`echo $line | awk '{print $1}'`
	grep  $key server.properties
	if (($? != 0 ))
	then
		echo $key >>notFoundinSetting
       fi
done <mustachewalla

>notFoundinServer
while read line
do
	key=`echo $line | awk '{FS="="}{print $1}'`
	grep $key settingwalla
	if (( $? != 0 ))
	then
		echo $line >> notFoundinServer
	fi

done < server.properties
