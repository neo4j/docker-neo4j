#!/bin/bash -e

SRCFOLDER=$(dirname $0)
OUTFOLDER="/certgen"
ENCRYPT_PASSPHRASE=false
SUBJ="/C=SE/O=Example/OU=ExampleCluster/CN=localhost"

while true; do
  case "$1" in
    -f | --folder ) OUTFOLDER="$2"; shift 2 ;;
    -p | --passphrase ) PASSPHRASE="$2"; shift 2 ;;
    -e | --encrypt ) ENCRYPT_PASSPHRASE=true; shift ;;
    * ) break ;;
  esac
done
# echo "OUTFOLDER: ${OUTFOLDER}"
# echo "PASSPHRASE: ${PASSPHRASE}"
# echo "DO ENCRYPT: ${ENCRYPT_PASSPHRASE}"

if ${ENCRYPT_PASSPHRASE} && [ -z "${PASSPHRASE}" ]; then
  echo >&2 "Passphrase encryption requested, but no passphrase given."
  exit 1
fi

echo "creating temporary CA"
CAFOLDER="${SRCFOLDER}/ca"
mkdir -p "${CAFOLDER}"
touch ${CAFOLDER}/testca.db
echo 01 > ${CAFOLDER}/testca.crt.srl
echo 01 > ${CAFOLDER}/testca.crl.srl

openssl req -new \
    -config "${SRCFOLDER}/ca.conf" \
    -out "${CAFOLDER}/testca.csr" \
    -keyout "${CAFOLDER}/testca.key"
echo "self-signing the CA certificate"
openssl ca -batch -selfsign -days 1 \
    -config "${SRCFOLDER}/ca.conf" \
    -keyfile "${CAFOLDER}/testca.key" \
    -in ${CAFOLDER}/testca.csr \
    -out ${CAFOLDER}/testca.crt \
    -outdir ${CAFOLDER} \
    -extensions signing_ca_ext

echo "Generating self signed SSL certificate into ${OUTFOLDER}"
if [ -n "${PASSPHRASE}" ]; then
  PASSPHRASE_OUT_ARG="-passout=pass:${PASSPHRASE}"
  PASSPHRASE_IN_ARG="-passin=pass:${PASSPHRASE}"
else
  PASSPHRASE_OUT_ARG="-nocrypt"
  PASSPHRASE_IN_ARG=
fi
openssl req -x509 -sha256 -nodes -newkey rsa:2048 -days 1 \
    -keyout "${OUTFOLDER}/private.key1" \
    -config "${SRCFOLDER}/server.conf" \
    -out "${OUTFOLDER}/selfsigned.crt" \
    -subj ${SUBJ}

echo "converting private key to pkcs8 format"
openssl pkcs8 -topk8 \
    -in "${OUTFOLDER}/private.key1" \
    -out "${OUTFOLDER}/private.key" \
    ${PASSPHRASE_OUT_ARG}
rm "${OUTFOLDER}/private.key1"


echo "making signing request for normal certificate"
openssl req -new \
    -config "${SRCFOLDER}/server.conf" \
    -key "${OUTFOLDER}/private.key" \
    -out "${OUTFOLDER}/casigned.csr" \
    ${PASSPHRASE_IN_ARG} \
    -subj ${SUBJ}
# openssl req -noout -text -in ${OUTFOLDER}/casigned.csr

echo "signing certificate as test CA"
openssl ca -batch -days 1 \
    -config "${SRCFOLDER}/ca.conf" \
    -in "${OUTFOLDER}/casigned.csr" \
    -out "${OUTFOLDER}/casigned.crt" \
    -keyfile "${CAFOLDER}/testca.key" \
    -extensions server_ext
# openssl x509 -noout -text -in ${OUTFOLDER}/casigned.crt

rm -rf "${CAFOLDER}"

if ${ENCRYPT_PASSPHRASE}; then
  echo "Creating encrypted passphrase file"
  echo "${PASSPHRASE}" > "${OUTFOLDER}/passfile"
  base64 -w 0 "${OUTFOLDER}/selfsigned.crt" | \
      openssl aes-256-cbc -a -salt \
        -pass stdin \
        -in "${OUTFOLDER}/passfile" \
        -out "${OUTFOLDER}/passphrase.enc"
  rm "${OUTFOLDER}/passfile"
fi

