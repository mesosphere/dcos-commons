#!/usr/bin/env python

import shutil
import sys

#cfg_fmtstr="""\
#frontend myvhost_frontend
#    bind *:{port}
#    mode http
#    use_backend myvhost_backend
#
#backend myvhost_backend
#    mode http
#    balance roundrobin
#    option forwardfor
#    http-request set-header X-Forwarded-Port %[dst_port]
#    http-request add-header X-Forwarded-Proto https if {{ ssl_fc }}
#{servers}
#"""

cfg_frontend_fmtstr="""\
frontend myfrontend
    bind *:{port}
    mode http
{acl}
{use_backend}
    default_backend {default_backend}
"""

cfg_backend_fmtstr="""\
backend {name}
    mode http
    balance roundrobin
    option forwardfor
    http-request set-header X-Forwarded-Port %[dst_port]
    http-request add-header X-Forwarded-Proto https if {{ ssl_fc }}
    http-request set-header Host {hostname}
    reqirep  "^([^ :]*)\ {incomingpath}/?(.*)" "\1\ {outgoingpath}/\2"
    server {name}x {fullhost}
"""

#"""
#backend myvhost_backend
#    mode http
#    balance roundrobin
#    option forwardfor
#    http-request set-header X-Forwarded-Port %[dst_port]
#    http-request add-header X-Forwarded-Proto https if {{ ssl_fc }}
#{servers}
#
#frontend myvhost_frontend
#    bind *:4040
#    mode http
#    acl acl1 path_beg /google
#    acl acl2 path_beg /example
#    use_backend myvhost_backend if acl1
#    use_backend example_backend if acl2
#
#
#backend myvhost_backend
#    mode http
#    balance roundrobin
#    option forwardfor
#    http-request set-header X-Forwarded-Port %[dst_port]
#    http-request add-header X-Forwarded-Proto https if { ssl_fc }
#    server bob google.com:80
#
#backend example_backend
#    mode http
#    balance roundrobin
#    option forwardfor
#    http-request set-header X-Forwarded-Port %[dst_port]
#    http-request add-header X-Forwarded-Proto https if { ssl_fc }
#    server bobo example.com:80
#"""
#server 1.b-h google.com:80
#server 2 example.com:80

def main():
    raw_cfg_path = sys.argv[1]
    cfg_path = sys.argv[2]
    port = sys.argv[3]
    backends = sys.argv[4]

    # shutil.copyfile(raw_cfg_path, cfg_path)

    # # 

    # # XXX make the default backend the first backend
    # cfg_str = cfg_fmtstr.format(port=port, servers="    "+backends)

    # with open(cfg_path, "a") as f:
    #         f.write(cfg_str)
    #         f.flush()

if __name__ == "__main__":
    main()
