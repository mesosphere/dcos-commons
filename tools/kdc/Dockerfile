FROM mesosphere/kdc:latest

RUN mkdir /kdc
COPY run.sh /kdc/run.sh
COPY kdc.conf /etc/heimdal-kdc/kdc.conf
RUN chown -R nobody:nogroup /kdc
RUN chmod -R 744 /var/lib/heimdal-kdc/
RUN chmod -R 744 /etc/heimdal-kdc/
RUN chmod -R 744 /kdc

CMD /kdc/run.sh
