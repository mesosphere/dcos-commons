FROM haproxy:1.7.1

COPY files/ /proxy-lite

RUN apt-get update && \
    apt-get install -y python3 && \
    rm -rf /var/lib/apt/lists/*

ENTRYPOINT []
CMD []
