FROM ubuntu:18.04

RUN apt-get update && apt-get install -y \
    openssl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /root
COPY openssl_ca.cnf openssl_intermediate.cnf generate_certs.sh ./

CMD /root/generate_certs.sh
