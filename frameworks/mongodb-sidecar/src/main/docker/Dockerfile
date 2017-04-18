FROM ubuntu:latest

# RUN apk update &&\
#     apk add --no-cache ca-certificates wget openssl\
#                        make gcc ruby-dev build-base libc-dev linux-headers &&\
#     update-ca-certificates
#
#
# RUN bundle config --global frozen 1

RUN apt-get update
RUN apt-get install -y software-properties-common
RUN apt-add-repository ppa:brightbox/ruby-ng
RUN apt-get update
RUN apt-get install -y patch gcc make wget g++
RUN apt-get install -y ruby2.3 ruby-switch ruby2.3-dev
RUN ruby-switch --set ruby2.3
RUN gem install bundler

# WORKDIR /app

COPY files/ /app
RUN cd /app && bundle install

# RUN wget -O /app/dumb-init https://github.com/Yelp/dumb-init/releases/download/v1.2.0/dumb-init_1.2.0_amd64
# RUN chmod +x /app/dumb-init
RUN chmod +x /app/sidecar.rb && \
    chmod +x /app/run.sh


# ENTRYPOINT ["/usr/local/bin/dumb-init", "--"]
# CMD ["/app/init.rb"]
ENTRYPOINT []
CMD []
