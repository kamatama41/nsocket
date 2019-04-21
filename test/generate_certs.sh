#!/usr/bin/env bash
#####
# https://jamielinux.com/docs/openssl-certificate-authority/
#####

rm -rf /root/ca && mkdir -p /root/ca/intermediate
cd /root/ca
cp /root/openssl_ca.cnf openssl.cnf
cp /root/openssl_intermediate.cnf intermediate/openssl.cnf

PASSWORD=passw0rd

## https://jamielinux.com/docs/openssl-certificate-authority/create-the-root-pair.html
mkdir certs crl newcerts private
chmod 700 private
touch index.txt
echo 1000 > serial

openssl genrsa -passout pass:${PASSWORD} -aes256 -out private/ca.key.pem 4096
openssl req -passin pass:${PASSWORD} -config openssl.cnf \
  -key private/ca.key.pem -new -x509 -days 7300 -sha256 \
  -extensions v3_ca -out certs/ca.cert.pem <<INPUT
GB
England

Alice Ltd
Alice Ltd Certificate Authority
Alice Ltd Root CA

INPUT
chmod 444 certs/ca.cert.pem

## https://jamielinux.com/docs/openssl-certificate-authority/create-the-intermediate-pair.html
cd /root/ca/intermediate
mkdir certs crl csr newcerts private
chmod 700 private
touch index.txt
echo 1000 > serial
echo 1000 > /root/ca/intermediate/crlnumber

cd /root/ca
openssl genrsa -passout pass:${PASSWORD} -aes256 -out intermediate/private/intermediate.key.pem 4096
chmod 400 intermediate/private/intermediate.key.pem

cd /root/ca
openssl req -passin pass:${PASSWORD} -config intermediate/openssl.cnf \
  -new -sha256 -key intermediate/private/intermediate.key.pem \
  -out intermediate/csr/intermediate.csr.pem <<INPUT
GB
England

Alice Ltd
Alice Ltd Certificate Authority
Alice Ltd Intermediate CA

INPUT

openssl ca -passin pass:${PASSWORD} -config openssl.cnf \
  -extensions v3_intermediate_ca -days 3650 -notext \
  -md sha256 -in intermediate/csr/intermediate.csr.pem \
  -out intermediate/certs/intermediate.cert.pem <<INPUT
y
y
INPUT

cat intermediate/certs/intermediate.cert.pem certs/ca.cert.pem > intermediate/certs/ca-chain.cert.pem
chmod 444 intermediate/certs/ca-chain.cert.pem

## https://jamielinux.com/docs/openssl-certificate-authority/sign-server-and-client-certificates.html
function signCertificate() {
  common_name=${1}
  days=${2}
  cert_type=${3}

  cd /root/ca
  openssl genrsa -aes256 -passout pass:${PASSWORD} -out intermediate/private/${common_name}.key.pem 2048
  chmod 400 intermediate/private/${common_name}.key.pem

  cd /root/ca
  openssl req -passin pass:${PASSWORD} -config intermediate/openssl.cnf \
    -key intermediate/private/${common_name}.key.pem \
    -new -sha256 -out intermediate/csr/${common_name}.csr.pem <<INPUT
US
California
Mountain View
Alice Ltd
Alice Ltd Web Services
${common_name}

INPUT

  cd /root/ca
  openssl ca -passin pass:${PASSWORD} -config intermediate/openssl.cnf \
    -extensions ${cert_type} -days ${days} -notext -md sha256 \
    -in intermediate/csr/${common_name}.csr.pem \
    -out intermediate/certs/${common_name}.cert.pem <<INPUT
y
y
INPUT
  chmod 444 intermediate/certs/${common_name}.cert.pem

  # Generate P12 file
  openssl pkcs12 -export -out /root/work/${common_name}.p12 \
    -in /root/ca/intermediate/certs/${common_name}.cert.pem \
    -inkey /root/ca/intermediate/private/${common_name}.key.pem \
    -passin pass:${PASSWORD} -passout pass:${PASSWORD}
}

signCertificate nsocket.server 1000 server_cert
signCertificate nsocket.client 1000 usr_cert

# Generate P12 file for CA
openssl pkcs12 -export -out /root/work/ca-chain.p12 \
  -in /root/ca/intermediate/certs/ca-chain.cert.pem \
  -inkey /root/ca/intermediate/private/intermediate.key.pem \
  -passin pass:${PASSWORD} -passout pass:${PASSWORD}
