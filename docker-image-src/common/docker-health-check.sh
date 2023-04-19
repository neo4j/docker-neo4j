#!/bin/bash -eu

_conf_file="${NEO4J_HOME}"/conf/neo4j.conf

while [ ! -e $_conf_file ]
do
  sleep 1
done

if [ $(grep -P "^(?!#)server.http.listen_address" $_conf_file) ]
then
  LISTEN_ADDRESS_SETTING_STRING=$(grep -P "^(?!#)server.http.listen_address" $_conf_file)
  LISTEN_ADDRESS_VALUE=${LISTEN_ADDRESS_SETTING_STRING#*=}
else
  LISTEN_ADDRESS_VALUE=""
fi

LISTEN_ADDRESS_SETTING_VALUE=${LISTEN_ADDRESS_VALUE}
if [ -z "${LISTEN_ADDRESS_SETTING_VALUE}" ]
then
  LISTEN_ADDRESS="http://localhost:7474/"
elif [[ ${LISTEN_ADDRESS_SETTING_VALUE} =~ (.){1,}:(\d){0,5} ]]
then
  LISTEN_ADDRESS=${LISTEN_ADDRESS_SETTING_VALUE}
elif [[ ${LISTEN_ADDRESS_SETTING_VALUE} =~ :(\d){0,5} ]]
then
  LISTEN_ADDRESS="http://localhost""${LISTEN_ADDRESS_SETTING_VALUE}""/"
else
  LISTEN_ADDRESS="${LISTEN_ADDRESS_SETTING_VALUE}"":7474/"
fi

if wget --accept=application/json ${LISTEN_ADDRESS} --output-document=tempfile
then
  exit 0
else
  exit 1
fi