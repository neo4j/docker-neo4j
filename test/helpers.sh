docker_cleanup() {
  local cid="$1"
  # Place logs in similarly named file
  mkdir -p tmp/out
  local l_logfile="tmp/out/${cid}.log"

  docker logs "${cid}" > "${l_logfile}" || echo "failed to write log"
  docker rm --force "${cid}" >/dev/null
}

docker_restart() {
  local l_cname="$1"
  docker restart "${l_cname}" >/dev/null
}

docker_run() {
  local l_image="$1" l_cname="$2"; shift; shift

  local envs=()
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

  docker-compose --file "${l_composefile}" --project-name neo4jcomposetest logs --no-color > "${l_logfile}" || echo "failed to write compose log"
  docker-compose --file "${l_composefile}" --project-name neo4jcomposetest down --volumes > /dev/null
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
  docker inspect --format '{{ .NetworkSettings.Networks.neo4jcomposetest_lan.IPAddress }}' "${l_cname}"
}

docker_ip() {
  local l_cname="$1"
  docker inspect --format '{{ .NetworkSettings.IPAddress }}' "${l_cname}"
}

neo4j_wait() {
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$(curl --silent --write-out '%{http_code}' ${auth:-} --output /dev/null http://${l_ip}:7474)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_wait_for_ha_available() {
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$(curl --silent --write-out '%{http_code}' ${auth:-} --output /dev/null http://${l_ip}:7474/db/manage/server/ha/available)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_wait_for_ha_master() {
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$(curl --silent --write-out '%{http_code}' ${auth:-} --output /dev/null http://${l_ip}:7474/db/manage/server/ha/master)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_wait_for_ha_slave() {
  local l_time="${3:-30}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi

  while true; do
    [[ "200" = "$(curl --silent --write-out '%{http_code}' ${auth:-} --output /dev/null http://${l_ip}:7474/db/manage/server/ha/slave)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}

neo4j_createnode() {
  local l_ip="$1" end="$((SECONDS+30))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi
  [[ "201" = "$(curl --silent --write-out '%{http_code}' --request POST --output /dev/null ${auth:-} http://${l_ip}:7474/db/data/node)" ]] || exit 1
}

neo4j_readnode() {
  local l_time="${3:-5}"
  local l_ip="$1" end="$((SECONDS+${l_time}))"
  if [[ -n "${2:-}" ]]; then
    local auth="--user $2"
  fi
  while true; do
    [[ "200" = "$(curl --silent --write-out '%{http_code}' ${auth:-} --output /dev/null http://${l_ip}:7474/db/data/node/0)" ]] && break
    [[ "${SECONDS}" -ge "${end}" ]] && exit 1
    sleep 1
  done
}
