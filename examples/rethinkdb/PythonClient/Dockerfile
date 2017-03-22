FROM centos

MAINTAINER Art Rand <arand@mesosphere.com>

RUN yum install -y epel-release && \
    yum install -y gcc && \
    yum install -y python-devel.x86_64 && \
    yum install -y python-devel.x86_64 && \
    yum install -y python-pip

RUN pip install ipython rethinkdb

ENTRYPOINT /bin/bash
