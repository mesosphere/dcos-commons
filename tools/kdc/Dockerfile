FROM nvaziri/kdc:dev

RUN mkdir /kdc
COPY run.sh /kdc/run.sh
COPY kdc.conf /etc/heimdal-kdc/kdc.conf
RUN chown -R nobody:nogroup /kdc
RUN chmod -R 700 /kdc

CMD /kdc/run.sh
