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

echo "generating private key"
if [ -n "${PASSPHRASE}" ]; then # if a passphrase was set
  PASSPHRASE_IN_ARG="-passin=pass:${PASSPHRASE}"
  PASSPHRASE_OUT_ARG="-passout=pass:${PASSPHRASE}"
else # if no passphrase
  PASSPHRASE_IN_ARG=
  PASSPHRASE_OUT_ARG="-nocrypt"
fi

echo "Generating self signed SSL certificate into ${OUTFOLDER}"
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
rm "${OUTFOLDER}/selfsigned.crt"

echo "Generating certificate sign request"
openssl req -new -nodes -utf8 \
    -key "${OUTFOLDER}/private.key" \
    -config server.conf \
    -extensions csr_reqext \
    -out "${OUTFOLDER}/selfsigned.csr" \
    -subj ${SUBJ} \
     ${PASSPHRASE_IN_ARG}

echo "making signing request for normal certificate"
openssl req -x509 -nodes -utf8 -days 1 \
    -in "${OUTFOLDER}/selfsigned.csr" \
    -key "${OUTFOLDER}/private.key" \
    -config server.conf \
    -extensions server_reqext \
    -out "${OUTFOLDER}/selfsigned.crt" \
     ${PASSPHRASE_IN_ARG}

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

