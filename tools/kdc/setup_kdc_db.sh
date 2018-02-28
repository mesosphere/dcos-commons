#!/usr/bin/expect

spawn /usr/local/sbin/kdb5_util create -s -r $env(REALM)
expect "Enter KDC database master key: "
send -- "password\r"
expect "Re-enter KDC database master key to verify: "
send -- "password\r"
expect "\r"
send -- "exit\r"
expect eof
