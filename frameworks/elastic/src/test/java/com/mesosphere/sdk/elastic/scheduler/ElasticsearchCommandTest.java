package com.mesosphere.sdk.elastic.scheduler;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ElasticsearchCommandTest {
    private ElasticsearchCommand elasticsearchCommand;

    @Before
    public void before() {
        elasticsearchCommand = new ElasticsearchCommand("5.0.0-beta1",
            "https://artifacts.elastic.co/downloads/packs/x-pack/x-pack-5.0.0-beta1.zip",
            "analysis-uci, analysis-kuromoji",
            "elastic",
            9307,
            "https://s3-us-west-1.amazonaws.com/hello-universe-assets/elasticsearch-statsd-5.0.0-beta1-SNAPSHOT.zip"
        );
    }

    @Test
    public void getCommandLineInvocation() throws Exception {
        // master node
        String commandLineInvocation = elasticsearchCommand.getCommandLineInvocation("master-1", 9207, 9307, "master-1-container-path");
        Assert.assertEquals(commandLineInvocation, "export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/x-pack-5.0.0-beta1.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/elasticsearch-statsd-5.0.0-beta1-SNAPSHOT.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-uci && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-kuromoji && $MESOS_SANDBOX/dns_utils/wait_dns.sh && exec $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch -Enode.name=master-1 -Ediscovery.zen.ping.unicast.hosts=master-0.elastic.mesos:9307,master-1.elastic.mesos:9307,master-2.elastic.mesos:9307 -Enode.master=true -Epath.data=master-1-container-path/data -Ecluster.name=elastic -Ebootstrap.ignore_system_bootstrap_checks=true -Ediscovery.zen.minimum_master_nodes=2 -Enode.data=false -Etransport.tcp.port=9307 -Enetwork.host=_site_,_local_ -Enode.ingest=false -Emetrics.statsd.host=$STATSD_UDP_HOST -Ehttp.port=9207 -Ebootstrap.memory_lock=true -Epath.logs=master-1-container-path/logs -Emetrics.statsd.port=$STATSD_UDP_PORT");
        // data node
        commandLineInvocation = elasticsearchCommand.getCommandLineInvocation("data-2", 9201, 9302, "data-2-container-path");
        Assert.assertEquals(commandLineInvocation, "export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/x-pack-5.0.0-beta1.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/elasticsearch-statsd-5.0.0-beta1-SNAPSHOT.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-uci && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-kuromoji && $MESOS_SANDBOX/dns_utils/wait_dns.sh && exec $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch -Enode.name=data-2 -Ediscovery.zen.ping.unicast.hosts=master-0.elastic.mesos:9307,master-1.elastic.mesos:9307,master-2.elastic.mesos:9307 -Enode.master=false -Epath.data=data-2-container-path/data -Ecluster.name=elastic -Ebootstrap.ignore_system_bootstrap_checks=true -Ediscovery.zen.minimum_master_nodes=2 -Enode.data=true -Etransport.tcp.port=9302 -Enetwork.host=_site_,_local_ -Enode.ingest=false -Emetrics.statsd.host=$STATSD_UDP_HOST -Ehttp.port=9201 -Ebootstrap.memory_lock=true -Epath.logs=data-2-container-path/logs -Emetrics.statsd.port=$STATSD_UDP_PORT");
        // ingest node
        commandLineInvocation = elasticsearchCommand.getCommandLineInvocation("ingest-10", 9210, 9310, "ingest-10-container-path");
        Assert.assertEquals(commandLineInvocation, "export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/x-pack-5.0.0-beta1.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/elasticsearch-statsd-5.0.0-beta1-SNAPSHOT.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-uci && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-kuromoji && $MESOS_SANDBOX/dns_utils/wait_dns.sh && exec $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch -Enode.name=ingest-10 -Ediscovery.zen.ping.unicast.hosts=master-0.elastic.mesos:9307,master-1.elastic.mesos:9307,master-2.elastic.mesos:9307 -Enode.master=false -Epath.data=ingest-10-container-path/data -Ecluster.name=elastic -Ebootstrap.ignore_system_bootstrap_checks=true -Ediscovery.zen.minimum_master_nodes=2 -Enode.data=false -Etransport.tcp.port=9310 -Enetwork.host=_site_,_local_ -Enode.ingest=true -Emetrics.statsd.host=$STATSD_UDP_HOST -Ehttp.port=9210 -Ebootstrap.memory_lock=true -Epath.logs=ingest-10-container-path/logs -Emetrics.statsd.port=$STATSD_UDP_PORT");
        // coordinator node
        commandLineInvocation = elasticsearchCommand.getCommandLineInvocation("coordinator-0", 9221, 9322, "coordinator-0-container-path");
        Assert.assertEquals(commandLineInvocation, "export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/x-pack-5.0.0-beta1.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch file://$MESOS_SANDBOX/elasticsearch-statsd-5.0.0-beta1-SNAPSHOT.zip && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-uci && $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch-plugin install --batch analysis-kuromoji && $MESOS_SANDBOX/dns_utils/wait_dns.sh && exec $MESOS_SANDBOX/5.0.0-beta1/bin/elasticsearch -Enode.name=coordinator-0 -Ediscovery.zen.ping.unicast.hosts=master-0.elastic.mesos:9307,master-1.elastic.mesos:9307,master-2.elastic.mesos:9307 -Enode.master=false -Epath.data=coordinator-0-container-path/data -Ecluster.name=elastic -Ebootstrap.ignore_system_bootstrap_checks=true -Ediscovery.zen.minimum_master_nodes=2 -Enode.data=false -Etransport.tcp.port=9322 -Enetwork.host=_site_,_local_ -Enode.ingest=false -Emetrics.statsd.host=$STATSD_UDP_HOST -Ehttp.port=9221 -Ebootstrap.memory_lock=true -Epath.logs=coordinator-0-container-path/logs -Emetrics.statsd.port=$STATSD_UDP_PORT");
    }

}