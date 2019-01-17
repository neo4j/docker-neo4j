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

  docker logs "${cid}" >> "${l_logfile}" 2>&1 || echo "failed to write log"
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

docker_run_with_volume() {
  local l_image="$1" l_cname="$2" l_volume="$3"; shift; shift; shift

  local envs=()
  if [[ ! "$@" =~ "NEO4J_ACCEPT_LICENSE_AGREEMENT=no" ]]; then
    envs+=("--env=NEO4J_ACCEPT_LICENSE_AGREEMENT=yes")
  fi
  for env in "$@"; do
    envs+=("--env=${env}")
  done
  local cid
  cid="$(docker run --detach "${envs[@]}" --name="${l_cname}" --volume="${l_volume}" "${l_image}")"
  echo "log: tmp/out/${cid}.log"
  trap "docker_cleanup ${cid}" EXIT
}

docker_run_with_volume_and_user() {
  local l_image="$1" l_cname="$2" l_volume="$3" l_user="$4"; shift; shift; shift; shift

  local envs=()
  if [[ ! "$@" =~ "NEO4J_ACCEPT_LICENSE_AGREEMENT=no" ]]; then
    envs+=("--env=NEO4J_ACCEPT_LICENSE_AGREEMENT=yes")
  fi
  for env in "$@"; do
    envs+=("--env=${env}")
  done
  local cid
  cid="$(docker run --detach "${envs[@]}" --name="${l_cname}" --volume="${l_volume}" --user="${l_user}" "${l_image}")"
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
  local l_image="$1" l_composefile="$2" l_cname="$3" l_rname="$4"; logs_d="$5"; shift; shift; shift; shift;

  # Create the log directories. If we let docker create them then they will be owned by docker not our current user
  # TODO: use some jq/yq magic to read out the volumes from the docker compose file
  mkdir --parents "${logs_d}/core1"
  mkdir --parents "${logs_d}/core2"
  mkdir --parents "${logs_d}/core3"
  mkdir --parents "${logs_d}/readreplica1"

  sed --in-place -e "s|image: .*|image: ${l_image}|g" "${l_composefile}"
  sed --in-place -e "s|container_name: core.*|container_name: ${l_cname}|g" "${l_composefile}"
  sed --in-place -e "s|container_name: read.*|container_name: ${l_rname}|g" "${l_composefile}"
  sed --in-place -e "s|LOGS_DIR|${logs_d}|g" "${l_composefile}"
  sed --in-place -e "s|USER_INFO|$(id -u):$(id -g)|g" "${l_composefile}"

  echo "logs: ${l_composefile}.log and ${logs_dir}"

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
    [[ "${SECONDS}" -ge "${end}" ]] && echo "timed out waiting for neo4j" && exit 1
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

uid_of() {
  stat -c %u "$1"
}

gid_of() {
  stat -c %g "$1"
}

get_completed_container_output() {
    # Gets the log output for a container that we want to run to completion
    # also rms the container
    local container_id="$1"; shift;
    local output_file="$1"; shift;
    local deadline="$((SECONDS+60))"
    readonly deadline
    while true; do
        [[ "${SECONDS}" -ge "${deadline}" ]] && echo "timed out waiting for container to finish" && exit 1
        sleep 1
        # Wait until the container isn't running any more
        if ! docker top "${container_id}" &> /dev/null; then

            ( docker logs "${container_id}" || echo "no logs found" ) > "${output_file}.stdout" 2>"${output_file}.stderr"

            if [[ "$(docker inspect "${container_id}" --format='{{.State.ExitCode}}')" -ne 0 ]]; then
                cat "${output_file}."*
                echo "ERROR: container exited with an error code"
                exit 1
            fi

            docker rm "${container_id}" || echo "error removing container"
            break
        fi
    done
}
