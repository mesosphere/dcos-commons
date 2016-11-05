package com.mesosphere.sdk.elastic.scheduler;

import com.google.common.base.Joiner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class KibanaCommand {
    private final String kibanaVerName;
    private final String xpackUri;

    KibanaCommand(String kibanaVerName, String xpackUri) {
        this.kibanaVerName = kibanaVerName;
        this.xpackUri = xpackUri;
    }

    String getCommandLineInvocation() {
        List<String> commands = new ArrayList<>();
        String binDir = String.format("$MESOS_SANDBOX/%1$s/bin", kibanaVerName);
        Path path = Paths.get(xpackUri).getFileName();
        if (path != null) {
            String xpackFilename = path.toString();
            String xpackFilePath = String.format("file://$MESOS_SANDBOX/%1$s", xpackFilename);
            commands.add(String.format("%1$s/kibana-plugin install " + xpackFilePath, binDir));
        }
        commands.add(String.format("exec %1$s/kibana -c kibana.yml", binDir));
        return Joiner.on(" && ").join(commands);
    }
}
