#!/usr/bin/env bash

set -o pipefail -o errtrace -o errexit -o nounset
# ubuntu in ci might not have bash 4.4
if [[ -n "$(shopt | grep inherit_errexit)" ]] ; then
  shopt -s inherit_errexit
fi

[[ -n "${TRACE:-}" ]] && set -o xtrace

declare errmsg="ERROR (${0##*/})":
trap 'echo >&2 $errmsg trap on error \(rc=${PIPESTATUS[@]}\) near line $LINENO' ERR

EXEC=(docker exec --interactive "${NETWORK_CONTAINER:?Network container name unset}")
CURL=(curl --silent --write-out '%{http_code}' --output /dev/null --connect-timeout 10)

docker_cleanup() {
  local cid="$1"
  # Place logs in similarly named file
  mkdir -p tmp/out
  local l_logfile="tmp/out/${cid}.log"

  docker logs "${cid}" > "${l_logfile}" || echo "failed to write log"
  docker rm --force "${cid}" > /dev/null 2>&1 || true
  docker network disconnect "${COMPOSE_NETWORK}" "${NETWORK_CONTAINER}" > /dev/null 2>&1 || true
  docker rm --force "${NETWORK_CONTAINER}" > /dev/null 2>&1 || true
}

docker_ensure_network_container() {
  if ! output=$(docker inspect "${NETWORK_CONTAINER:?Network container name unset}" 2> /dev/null); then
    network_container_image_id=$(cat tmp/.image-id-network-container)
    docker run --rm --detach --interactive --name="${NETWORK_CONTAINER:?Network container name unset}" > /dev/null \
    "${network_container_image_id}"
  fi
  docker network connect "${COMPOSE_NETWORK}" "${NETWORK_CONTAINER}" > /dev/null 2>&1 || true
}

docker_restart() {
  local l_cname="$1"
  docker restart "${l_cname}" >/dev/null
}

docker_run() {
  local l_image="$1" l_cname="$2"; shift; shift

  local envs=()
  if [[ ! "$@" =~ "NEO4J_ACCEPT_LICENSE_AGREEMENT=no" ]]; then
    envs+=("--env=NEO4J_ACCEPT_LICENSE_AGREEMENT=yes")
  fi
  for env in "$@"; do
    envs+=("--env=${env}")
  done
  local cid="$(docker run --detach "${envs[@]}" --name="${l_cname}" "${l_image}")"
  echo "log: tmp/out/${cid}.log"
  trap "docker_cleanup ${cid}" EXIT
}

docker_compose_cleanup() {
  local l_composefile="$1"
  # Place compose logs in similarly named file
  local l_logfile="${1}.log"

  docker network disconnect "${COMPOSE_NETWORK}" "${NETWORK_CONTAINER}" > /dev/null 2>&1 || true
  docker-compose --file "${l_composefile}" --project-name neo4jcomposetest logs --no-color > "${l_logfile}" || echo "failed to write compose log"
  docker-compose --file "${l_composefile}" --project-name neo4jcomposetest down --volumes > /dev/null
  docker rm --force "${NETWORK_CONTAINER}" > /dev/null 2>&1 || true
}

docker_compose_up() {
  local l_image="$1" l_composefile="$2" l_cname="$3" l_rname="$4"; shift; shift; shift; shift;
  sed --in-place -e "s|image: .*|image: ${l_image}|g" "${l_composefile}"
  sed --in-place -e "s|container_name: core.*|container_name: ${l_cname}|g" "${l_composefile}"
  sed --in-place -e "s|container_name: read.*|container_name: ${l_rname}|g" "${l_composefile}"

  echo "logs: ${l_composefile}.log"

  docker-compose --file "${l_composefile}" --project-name neo4jcomposetest up -d
  trap "docker_compose_cleanup ${l_composefile}" EXIT
}

docker_compose_ip() {
  local l_cname="$1"
  docker inspect --format "{{ .NetworkSettings.Networks.${COMPOSE_NETWORK}.IPAddress }}" "${l_cname}"
}

docker_ip() {
  local l_cname="$1"
  docker inspect --format '{{ .NetworkSettings.IPAddress }}' "${l_cname}"
}

neo4j_wait() {
  docker_ensure_network_container
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$("${EXEC[@]}" "${CURL[@]}" ${auth:-} http://${l_ip}:7474)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_wait_for_ha_available() {
  docker_ensure_network_container
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$("${EXEC[@]}" "${CURL[@]}" ${auth:-} http://${l_ip}:7474/db/manage/server/ha/available)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_wait_for_ha_master() {
  docker_ensure_network_container
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$("${EXEC[@]}" "${CURL[@]}" ${auth:-} http://${l_ip}:7474/db/manage/server/ha/master)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_wait_for_ha_slave() {
  docker_ensure_network_container
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$("${EXEC[@]}" "${CURL[@]}" ${auth:-} http://${l_ip}:7474/db/manage/server/ha/slave)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_createnode() {
  docker_ensure_network_container
  local l_ip="$1" end="$((SECONDS+30))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi
  [[ "201" = "$("${EXEC[@]}" "${CURL[@]}" ${auth:-} --request POST http://${l_ip}:7474/db/data/node)" ]] || exit 1
}

neo4j_readnode() {
  docker_ensure_network_container
  local l_time="${3:-5}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi
  while true; do
    [[ "200" = "$("${EXEC[@]}" "${CURL[@]}" ${auth:-} http://${l_ip}:7474/db/data/node/0)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}
