#!/usr/bin/env python

import shutil
import sys

acl_fmtstr="""    acl {aclname} path_beg {externalroute}
"""

use_backend_fmtstr="""    use_backend {backendname} if {aclname}
"""

cfg_frontend_fmtstr="""\
frontend proxylite_frontend
    option httplog
    bind *:{port}
    mode http
    acl acl_root_redirect path /
{acl}
    redirect prefix {root_redirect} code 307 if acl_root_redirect
{use_backend}

"""

cfg_backend_fmtstr="""\
backend {name}
    mode http
    balance roundrobin
    option forwardfor
    http-request set-header X-Forwarded-Port %[dst_port]
    http-request add-header X-Forwarded-Proto https if {{ ssl_fc }}
    http-request set-header Host {hostname}
    reqirep  "^([^ :]*)\ {incomingpath}/?(.*)" "\\1\ {outgoingpath}/\\2"
    acl hdr_location res.hdr(Location) -m found
    rspirep "^Location: (https?://{hostname}(:[0-9]+)?)?{outgoingpath}(/.*)" "Location: {incomingpath}\\3" if hdr_location
    server x{name}x {fullhost}

"""

def main():
    log("starting")

    selfname = sys.argv[1]
    raw_cfg_path = sys.argv[2]
    cfg_path = sys.argv[3]
    proxyport = sys.argv[4]
    externalroutes = sys.argv[5]
    internalroutes = sys.argv[6]
    rootredirect = sys.argv[7]

    log("copying {} to {}".format(raw_cfg_path, cfg_path))
    shutil.copyfile(raw_cfg_path, cfg_path)

    log("verifying config")
    c = ConfigMaker(proxyport, externalroutes, internalroutes, rootredirect)
    if not c.valid:
        crash("invalid config")

    log("generating config")
    cfg_str = c.generate()

    log("writing config to {}".format(cfg_path))
    with open(cfg_path, "a") as f:
            f.write(cfg_str)
            f.flush()

    log("done")

class Config(object):
    def __init__(self):
        self.proxyport = None
        self.rootredirect = None

        self.externalpath = {}
        self.internalpath = {}
        self.internalport = {}
        self.internalhost = {}

        self.keys = []

class ConfigMaker(object):
    """
    What we need:
    port proxylite binds to (4040)
    what path to expose (/google_fake)
    what path to proxy to (/fake)
    the hostname we proxy to (google.com)
    the port to proxy to (80)

    proposed input:
    PROXY_PORT=4040
    EXTERNAL_ROUTES=/v1,/google,/example
    INTERNAL_ROUTES=web-0-server.proxylite.mesos:4041/myapp,google.com:80/mygoog,example.com:80/myapp
    """
    def __init__(self, proxyport, externalroutes, internalroutes, rootredirect):
        self.valid = False
        self.c = Config()

        allargs = [externalroutes, internalroutes]
        if not self.v_numargs(allargs):
            return
        if not self.unpack(proxyport, externalroutes, internalroutes, rootredirect):
            return

        # Always last
        self.valid = True

    def unpack(self, proxyport, externalroutes, internalroutes, rootredirect):
        """
        Return True if successful, false otherwise
        """
        self.c.proxyport = proxyport
        self.c.rootredirect = rootredirect
        keys = self.mk_keys(internalroutes)

        self.c.keys = keys

        for i, exr in list(enumerate(externalroutes.split(","))):
            k = keys[i]
            self.c.externalpath[k] = exr

        for i, inr in list(enumerate(internalroutes.split(","))):
            k = keys[i]
            hostname, port, path = self.parse_inr(inr)
            self.c.internalhost[k] = hostname
            self.c.internalport[k] = port
            self.c.internalpath[k] = path
        return True

    def parse_inr(self, inr):
        portsplit = inr.split(":", 1)
        if len(portsplit) == 1:
            # No :
            routesplit = inr.split("/", 1)
            if len(routesplit) == 2:
                # No : yes /
                return (routesplit[0], "", "/{}".format(routesplit[1]))
            # No : No /
            return (routesplit[0], "", "")
        # Yes :
        routesplit = portsplit[1].split("/")
        if len(routesplit) == 1:
            # Yes : No /
            return (portsplit[0], portsplit[1], "")
        # Yes : Yes /
        return (portsplit[0], routesplit[0], "/{}".format(routesplit[1]))

    def mk_keys(self, internalroutes):
        keys = []
        for s in internalroutes.split(","):
            keys.append(s.replace("/", "-_"))
        return keys

    def generate(self):
        """
        Returns the entire config as a string
        """

        acl_str = ""
        use_backend_str = ""

        for i, k in list(enumerate(self.c.keys)):
            aclname = "acl{}".format(i)
            exr = self.c.externalpath[k]
            acl_str += acl_fmtstr.format(aclname=aclname, externalroute=exr)
            use_backend_str += use_backend_fmtstr.format(backendname=k, aclname=aclname)

        cfg_frontend_str = cfg_frontend_fmtstr.format(port=self.c.proxyport,
                acl=acl_str,
                use_backend=use_backend_str,
                root_redirect=self.c.rootredirect)

        cfg_backend_str = ""
        for k in self.c.keys:
            fullhost = "{}:{}".format(self.c.internalhost[k], self.c.internalport[k])
            cfg_backend_str += cfg_backend_fmtstr.format(name=k,
                    hostname=self.c.internalhost[k],
                    incomingpath=self.c.externalpath[k],
                    outgoingpath=self.c.internalpath[k],
                    fullhost=fullhost)

        return "\n{}\n{}".format(cfg_frontend_str, cfg_backend_str)

    def v_numargs(self, allargs):
        length = len(allargs[0].split(","))
        for s in allargs:
            if len(s.split(",")) != length:
                log("args have inconsistent length csv")
                return False
        return True


def log(msg):
    scriptname = sys.argv[0]
    selfname = sys.argv[1]
    logname = "[{} {}]".format(selfname, scriptname)
    print("{}: {}".format(logname, msg))

def crash(msg, exitcode=1):
    scriptname = sys.argv[0]
    selfname = sys.argv[1]
    logname = "[{} {}]".format(selfname, scriptname)
    print("{}: {}".format(logname, msg))
    sys.exit(exitcode)

if __name__ == "__main__":
    main()
