#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This file is sourced when running various Spark programs.
# Copy it as spark-env.sh and edit that to configure Spark for your site.

# Options read by executors and drivers running inside the cluster
export SPARK_LOCAL_DIRS={{MESOS_SANDBOX}}/scratch

# Options for the daemons used in the standalone deploy mode
export SPARK_WORKER_CORES={{WORKER_CPUS}}
export SPARK_WORKER_MEMORY="{{WORKER_MEM}}m"
export SPARK_WORKER_PORT={{SPARK_WORKER_PORT}}
export SPARK_DAEMON_MEMORY={{DAEMON_MEM}}
export SPARK_MASTER_HOST=master-0-server.{{FRAMEWORK_NAME}}.mesos
export SPARK_MASTER_PORT={{SPARK_MASTER_PORT}}
export SPARK_MASTER_WEBUI_PORT={{SPARK_MASTER_WEBUI_PORT}}
export SPARK_MASTER_OPTS={{SPARK_MASTER_OPTS}}
export SPARK_WORKER_DIR={{MESOS_SANDBOX}}/work
export SPARK_WORKER_OPTS={{SPARK_WORKER_OPTS}}

# Generic options for the daemons used in the standalone deploy mode
export SPARK_IDENT_STRING={{FRAMEWORK_NAME}}
