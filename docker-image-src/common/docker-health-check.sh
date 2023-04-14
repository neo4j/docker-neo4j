#!/bin/bash -eu

if [ -z "${NEO4J_server_http_listen__address-}" ]
then
  LISTEN_ADDRESS="http://localhost:7474/"
else
  LISTEN_ADDRESS="http://localhost""${NEO4J_server_http_listen__address}""/"
fi

if wget --accept=application/json ${LISTEN_ADDRESS} --output-document=tempfile
then
  exit 0
else
  exit 1
fi