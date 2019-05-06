# Build the kdc-api-server in a separate container
FROM golang:1.11
WORKDIR /go/src/github.com/mesosphere/kdc-api-server/server
RUN go get -d -v golang.org/x/net/html
COPY kdc-api-server/ /go/src/github.com/mesosphere/kdc-api-server
RUN go get github.com/dcos/client-go/dcos
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o app .

# Build a KDC container
FROM mesosphere/kdc:latest@sha256:7d8fa40153fde988df54d2b1f3f25cab3e9eac4fb20ab454096765eab9161198
RUN mkdir /kdc
COPY run.sh /kdc/run.sh
COPY kdc.conf /etc/heimdal-kdc/kdc.conf
RUN chown -R nobody:nogroup /kdc
RUN chmod -R 744 /var/lib/heimdal-kdc/
RUN chmod -R 744 /etc/heimdal-kdc/
RUN chmod -R 744 /kdc

COPY --from=0 /go/src/github.com/mesosphere/kdc-api-server/server/server /kdc

CMD /kdc/run.sh
