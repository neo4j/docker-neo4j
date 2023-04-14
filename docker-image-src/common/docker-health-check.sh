#!/bin/bash -eu

if wget --accept=application/json http://localhost:7474/ --output-document=tempfile
then
  exit 0
else
  exit 1
fi