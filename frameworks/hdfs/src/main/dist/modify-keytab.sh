export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jdk*/)
$JAVA_HOME/bin/java -jar krbtest.jar secrets-hdfs.keytab 
echo "Keytab file modified"
