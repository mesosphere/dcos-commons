#!/bin/sh
dcos cockroachdb cockroach sql "CREATE DATABASE bank;"
dcos cockroachdb cockroach sql "CREATE TABLE bank.accounts (id INT PRIMARY KEY, balance DECIMAL);"
dcos cockroachdb cockroach sql "INSERT INTO bank.accounts VALUES (1, 10000.50);"
dcos cockroachdb cockroach sql "INSERT INTO bank.accounts VALUES (2, 10000.50);"
dcos cockroachdb cockroach sql "SELECT * FROM bank.accounts;"
