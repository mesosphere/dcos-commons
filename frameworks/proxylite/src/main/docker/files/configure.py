#!/usr/bin/env python

import shutil
import sys

cfg_fmtstr="""\
frontend myvhost_frontend
    bind *:{port}
    mode http
    use_backend myvhost_backend

backend myvhost_backend
    mode http
    balance roundrobin
    option forwardfor
    http-request set-header X-Forwarded-Port %[dst_port]
    http-request add-header X-Forwarded-Proto https if {{ ssl_fc }}
{servers}
"""
#server 1.b-h google.com:80
#server 2 example.com:80

def main():
    raw_cfg_path = sys.argv[1]
    cfg_path = sys.argv[2]
    port = sys.argv[3]
    backends = sys.argv[4]

    shutil.copyfile(raw_cfg_path, cfg_path)
    cfg_str = cfg_fmtstr.format(port=port, servers="    "+backends)
    with open(cfg_path, "a") as f:
            f.write(cfg_str)
            f.flush()

if __name__ == "__main__":
    main()
