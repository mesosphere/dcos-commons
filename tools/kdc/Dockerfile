FROM ubuntu:16.04


RUN apt-get -y update && \
    apt-get -y install \
      bison \
      build-essential \
      byacc \
      curl \
      expect \
      less \
      vim \
      wget && \
    apt-get clean


# The following Kerberos distribution is derived from
# https://kerberos.org/dist/krb5/1.15/krb5-1.15.2.tar.gz
RUN wget https://s3-us-west-2.amazonaws.com/nima-dev/krb5-1.16.tar.gz
RUN tar xvf krb5-1.16.tar.gz  && rm krb5-1.16.tar.gz
RUN cd krb5-1.16/src && ./configure && make && make install

RUN mkdir /kdc && \
    chown -R nobody:nogroup /kdc && \
    chown -R nobody:nogroup /var/log/ && \
    chown -R nobody:nogroup /usr/local/

COPY krb5.conf /kdc/krb5.conf
COPY kdc.conf /kdc/kdc.conf
COPY setup_kdc_db.sh /setup_kdc_db.sh
RUN chown nobody:nogroup /setup_kdc_db.sh
COPY setup_kdc.sh /setup_kdc.sh
RUN chown nobody:nogroup /setup_kdc.sh

ENV REALM LOCAL
ENV KRB5_CONFIG /kdc/krb5.conf
ENV KRB5_KDC_PROFILE /kdc/kdc.conf
ENV PORT_KDC 2500

CMD "/setup_kdc.sh"
