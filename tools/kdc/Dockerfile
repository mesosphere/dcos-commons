FROM mesosphere/kdc:latest@sha256:7d8fa40153fde988df54d2b1f3f25cab3e9eac4fb20ab454096765eab9161198

RUN mkdir /kdc
COPY run.sh /kdc/run.sh
COPY kdc.conf /etc/heimdal-kdc/kdc.conf
RUN chown -R nobody:nogroup /kdc
RUN chmod -R 744 /var/lib/heimdal-kdc/
RUN chmod -R 744 /etc/heimdal-kdc/
RUN chmod -R 744 /kdc

CMD /kdc/run.sh
