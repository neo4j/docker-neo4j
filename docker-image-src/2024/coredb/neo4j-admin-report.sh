#!/bin/bash

# load useful utility functions
. /startup/utilities.sh

function find_report_destination
{
    local to_flag="--to-path"

    while [[ $# -gt 0 ]]; do
        case $1 in
    # for arg in "$@"; do
    #     case $arg in
            "${to_flag}"=*)
                echo ${1#*=}
                return
                ;;
            "${to_flag}")
                echo ${2}
                return
                ;;
            *)
                shift
                ;;
        esac
    done
    mkdir -p /tmp/reports
    echo "/tmp/reports"
}

report_cmd=("neo4j-admin" "server" "report")

# note, these debug messages are unlikely to work in a docker exec, since environment isn't preserved.
debug_msg "Called ${0}"
debug_msg "neo4j-admin report command is:" "${report_cmd[@]}" "$@"

# find report destination. This could be specified by argument to neo4j-admin or it could be the default location.
report_destination=$(find_report_destination "$@")
debug_msg "report_destination will be ${report_destination}"

debug_msg "Determining which user to run neo4j-admin as."
if running_as_root; then
    debug_msg "running neo4j-admin report as root"
    if [[ ! $(su-exec neo4j:neo4j test -w "${report_destination}") ]]; then
        debug_msg "reowning ${report_destination} to neo4j:neo4j"
        chown neo4j:neo4j "${report_destination}"
    fi
    debug_msg su-exec neo4j:neo4j "${report_cmd[@]}" "$@"
    su-exec neo4j:neo4j "${report_cmd[@]}" "$@"
else
    debug_msg "running neo4j-admin report as user defined by --user flag"
    if [[ ! -w "${report_destination}" ]]; then
        print_permissions_advice_and_fail "${report_destination}" "$(id -u)" "$(id -g)"
    fi
    debug_msg "${report_cmd[@]}" "$@"
    "${report_cmd[@]}" "$@"
fi