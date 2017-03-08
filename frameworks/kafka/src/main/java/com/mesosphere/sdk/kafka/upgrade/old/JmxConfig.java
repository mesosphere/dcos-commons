package com.mesosphere.sdk.kafka.upgrade.old;


/* copy from dcos-kafka-service */

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JmxConfig contains the configuration for the JVM jmx props for a Kafka
 * broker.
 * <p>
 * http://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html
 */
public class JmxConfig {
    @JsonProperty("enable")
    private boolean enable = false;

    @JsonProperty("remote")
    private boolean remote = false;

    @JsonProperty("remote_port")
    private int remotePort = -1;

    @JsonProperty("remote_registry_ssl")
    private boolean remoteRegistrySsl = false;

    @JsonProperty("remote_ssl")
    private boolean remoteSsl = false;

    @JsonProperty("remote_ssl_enabled_protocols")
    private String remoteSslEnabledProtocols;

    @JsonProperty("remote_ssl_enabled_cipher_suites")
    private String remoteSslEnabledCipherSuites;

    @JsonProperty("remote_ssl_need_client_auth")
    private boolean remoteSslNeedClientAuth = false;

    @JsonProperty("remote_authenticate")
    private boolean remoteAuthenticate = false;

    @JsonProperty("remote_password_file")
    private String remotePasswordFile;

    @JsonProperty("remote_access_file")
    private String remoteAccessFile;

    @JsonProperty("remote_login_config")
    private String remoteLoginConfig;

    public JmxConfig() {

    }

    public JmxConfig(boolean enable, boolean remote, int remotePort, boolean remoteSsl, boolean remoteAuthenticate) {
        super();
        this.enable = enable;
        this.remote = remote;
        this.remotePort = remotePort;
        this.remoteSsl = remoteSsl;
        this.remoteAuthenticate = remoteAuthenticate;
    }

    @JsonProperty("enable")
    public void setEnable(final boolean enable) {
        this.enable = enable;
    }

    @JsonIgnore
    public boolean isEnabled() {
        return enable;
    }

    @JsonIgnore
    public boolean isRemote() {
        return remote;
    }

    @JsonProperty("remote")
    public void setRemote(final boolean remote) {
        this.remote = remote;
    }

    public int getRemotePort() {
        return remotePort;
    }

    @JsonProperty("remote_port")
    public void setRemotePort(final int remotePort) {
        this.remotePort = remotePort;
    }

    @JsonIgnore
    public boolean isRemoteRegistrySsl() {
        return remoteRegistrySsl;
    }

    @JsonProperty("remote_registry_ssl")
    public void setRemoteRegistrySsl(final boolean remoteRegistrySsl) {
        this.remoteRegistrySsl = remoteRegistrySsl;
    }

    @JsonIgnore
    public boolean isRemoteSsl() {
        return remoteSsl;
    }

    @JsonProperty("remote_ssl")
    public void setRemoteSsl(final boolean remoteSsl) {
        this.remoteSsl = remoteSsl;
    }

    public String getRemoteSslEnabledProtocols() {
        return remoteSslEnabledProtocols;
    }

    @JsonProperty("remote_ssl_enabled_protocols")
    public void setRemoteSslEnabledProtocols(final String remoteSslEnabledProtocols) {
        this.remoteSslEnabledProtocols = remoteSslEnabledProtocols;
    }

    public String getRemoteSslEnabledCipherSuites() {
        return remoteSslEnabledCipherSuites;
    }

    @JsonProperty("remote_ssl_enabled_cipher_suites")
    public void setRemoteSslEnabledCipherSuites(final String remoteSslEnabledCipherSuites) {
        this.remoteSslEnabledCipherSuites = remoteSslEnabledCipherSuites;
    }

    @JsonIgnore
    public boolean isRemoteSslNeedClientAuth() {
        return remoteSslNeedClientAuth;
    }

    @JsonProperty("remote_ssl_need_client_auth")
    public void setRemoteSslNeedClientAuth(final boolean remoteSslNeedClientAuth) {
        this.remoteSslNeedClientAuth = remoteSslNeedClientAuth;
    }

    @JsonIgnore
    public boolean isRemoteAuthenticate() {
        return remoteAuthenticate;
    }

    @JsonProperty("remote_authenticate")
    public void setRemoteAuthenticate(final boolean remoteAuthenticate) {
        this.remoteAuthenticate = remoteAuthenticate;
    }

    public String getRemotePasswordFile() {
        return remotePasswordFile;
    }

    @JsonProperty("remote_password_file")
    public void setRemotePasswordFile(final String remotePasswordFile) {

        this.remotePasswordFile = remotePasswordFile;
    }

    public String getRemoteAccessFile() {
        return remoteAccessFile;
    }

    @JsonProperty("remote_access_file")
    public void setRemoteAccessFile(final String remoteAccessFile) {
        this.remoteAccessFile = remoteAccessFile;
    }

    public String getRemoteLoginConfig() {
        return remoteLoginConfig;
    }

    @JsonProperty("remote_login_config")
    public void setRemoteLoginConfig(final String remoteLoginConfig) {
        this.remoteLoginConfig = remoteLoginConfig;
    }

    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JmxConfig jmxConfig = (JmxConfig) o;

        if (enable != jmxConfig.enable) return false;
        if (remote != jmxConfig.remote) return false;
        if (remotePort != jmxConfig.remotePort) return false;
        if (remoteRegistrySsl != jmxConfig.remoteRegistrySsl) return false;
        if (remoteSsl != jmxConfig.remoteSsl) return false;
        if (remoteSslNeedClientAuth != jmxConfig.remoteSslNeedClientAuth) return false;
        if (remoteAuthenticate != jmxConfig.remoteAuthenticate) return false;
        if (remoteSslEnabledProtocols != null ?
                !remoteSslEnabledProtocols.equals(jmxConfig.remoteSslEnabledProtocols)
                : jmxConfig.remoteSslEnabledProtocols != null)
            return false;
        if (remoteSslEnabledCipherSuites != null ?
                !remoteSslEnabledCipherSuites.equals(jmxConfig.remoteSslEnabledCipherSuites)
                : jmxConfig.remoteSslEnabledCipherSuites != null)
            return false;
        if (remotePasswordFile != null ? !remotePasswordFile.equals(jmxConfig.remotePasswordFile)
                : jmxConfig.remotePasswordFile != null)
            return false;
        if (remoteAccessFile != null ? !remoteAccessFile.equals(jmxConfig.remoteAccessFile)
                : jmxConfig.remoteAccessFile != null)
            return false;
        return remoteLoginConfig != null ? remoteLoginConfig.equals(jmxConfig.remoteLoginConfig)
                : jmxConfig.remoteLoginConfig == null;

    }

    @Override
    public int hashCode() {
        int result = (enable ? 1 : 0);
        result = 31 * result + (remote ? 1 : 0);
        result = 31 * result + remotePort;
        result = 31 * result + (remoteRegistrySsl ? 1 : 0);
        result = 31 * result + (remoteSsl ? 1 : 0);
        result = 31 * result + (remoteSslEnabledProtocols != null ? remoteSslEnabledProtocols.hashCode() : 0);
        result = 31 * result + (remoteSslEnabledCipherSuites != null ? remoteSslEnabledCipherSuites.hashCode() : 0);
        result = 31 * result + (remoteSslNeedClientAuth ? 1 : 0);
        result = 31 * result + (remoteAuthenticate ? 1 : 0);
        result = 31 * result + (remotePasswordFile != null ? remotePasswordFile.hashCode() : 0);
        result = 31 * result + (remoteAccessFile != null ? remoteAccessFile.hashCode() : 0);
        result = 31 * result + (remoteLoginConfig != null ? remoteLoginConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "JmxConfig{" +
                "enable=" + enable +
                ", remote=" + remote +
                ", remotePort=" + remotePort +
                ", remoteRegistrySsl=" + remoteRegistrySsl +
                ", remoteSsl=" + remoteSsl +
                ", remoteSslEnabledProtocols='" + remoteSslEnabledProtocols + '\'' +
                ", remoteSslEnabledCipherSuites='" + remoteSslEnabledCipherSuites + '\'' +
                ", remoteSslNeedClientAuth=" + remoteSslNeedClientAuth +
                ", remoteAuthenticate=" + remoteAuthenticate +
                ", remotePasswordFile='" + remotePasswordFile + '\'' +
                ", remoteAccessFile='" + remoteAccessFile + '\'' +
                ", remoteLoginConfig='" + remoteLoginConfig + '\'' +
                '}';
    }
}
