#!/bin/bash -eu

if [ "$1" == "neo4j" ]; then

    # Env variable naming convention:
    # - prefix NEO4J_
    # - double underscore char '__' instead of single underscore '_' char in the setting name
    # - underscore char '_' instead of dot '.' char in the setting name
    # Example:
    # NEO4J_dbms_tx__log_rotation_retention__policy env variable to set
    #       dbms.tx_log.rotation.retention_policy setting

    # Backward compatibility - map old hardcoded env variables into new naming convention (if they aren't set already)
    # Set some to default values if unset
    : ${NEO4J_dbms_tx__log_rotation_retention__policy:=${NEO4J_dbms_txLog_rotation_retentionPolicy:-"100M size"}}
    : ${NEO4J_wrapper_java_additional:=${NEO4J_UDC_SOURCE:-"-Dneo4j.ext.udc.source=docker"}}
    : ${NEO4J_dbms_memory_heap_initial__size:=${NEO4J_dbms_memory_heap_maxSize:-"512M"}}
    : ${NEO4J_dbms_memory_heap_max__size:=${NEO4J_dbms_memory_heap_maxSize:-"512M"}}
    : ${NEO4J_dbms_unmanaged__extension__classes:=${NEO4J_dbms_unmanagedExtensionClasses:-}}
    : ${NEO4J_dbms_allow__format__migration:=${NEO4J_dbms_allowFormatMigration:-}}
    : ${NEO4J_dbms_connectors_default__advertised__address:=${NEO4J_dbms_connectors_defaultAdvertisedAddress:-}}
    : ${NEO4J_ha_server__id:=${NEO4J_ha_serverId:-}}
    : ${NEO4J_ha_initial__hosts:=${NEO4J_ha_initialHosts:-}}
    : ${NEO4J_causal__clustering_expected__core__cluster__size:=${NEO4J_causalClustering_expectedCoreClusterSize:-}}
    : ${NEO4J_causal__clustering_initial__discovery__members:=${NEO4J_causalClustering_initialDiscoveryMembers:-}}
    : ${NEO4J_causal__clustering_discovery__listen__address:=${NEO4J_causalClustering_discoveryListenAddress:-"0.0.0.0:5000"}}
    : ${NEO4J_causal__clustering_discovery__advertised__address:=${NEO4J_causalClustering_discoveryAdvertisedAddress:-"$(hostname):5000"}}
    : ${NEO4J_causal__clustering_transaction__listen__address:=${NEO4J_causalClustering_transactionListenAddress:-"0.0.0.0:6000"}}
    : ${NEO4J_causal__clustering_transaction__advertised__address:=${NEO4J_causalClustering_transactionAdvertisedAddress:-"$(hostname):6000"}}
    : ${NEO4J_causal__clustering_raft__listen__address:=${NEO4J_causalClustering_raftListenAddress:-"0.0.0.0:7000"}}
    : ${NEO4J_causal__clustering_raft__advertised__address:=${NEO4J_causalClustering_raftAdvertisedAddress:-"$(hostname):7000"}}

    : ${NEO4J_dbms_connectors_default__listen__address:="0.0.0.0"}
    : ${NEO4J_dbms_connector_http_listen__address:="0.0.0.0:7474"}
    : ${NEO4J_dbms_connector_https_listen__address:="0.0.0.0:7473"}
    : ${NEO4J_dbms_connector_bolt_listen__address:="0.0.0.0:7687"}
    : ${NEO4J_ha_host_coordination:="$(hostname):5001"}
    : ${NEO4J_ha_host_data:="$(hostname):6001"}

    # unset old hardcoded unsupported env variables
    unset NEO4J_dbms_txLog_rotation_retentionPolicy NEO4J_UDC_SOURCE \
        NEO4J_dbms_memory_heap_maxSize NEO4J_dbms_memory_heap_maxSize \
        NEO4J_dbms_unmanagedExtensionClasses NEO4J_dbms_allowFormatMigration \
        NEO4J_dbms_connectors_defaultAdvertisedAddress NEO4J_ha_serverId \
        NEO4J_ha_initialHosts NEO4J_causalClustering_expectedCoreClusterSize \
        NEO4J_causalClustering_initialDiscoveryMembers \
        NEO4J_causalClustering_discoveryListenAddress \
        NEO4J_causalClustering_discoveryAdvertisedAddress \
        NEO4J_causalClustering_transactionListenAddress \
        NEO4J_causalClustering_transactionAdvertisedAddress \
        NEO4J_causalClustering_raftListenAddress \
        NEO4J_causalClustering_raftAdvertisedAddress

    # Custom settings for dockerized neo4j
    : ${NEO4J_dbms_tx__log_rotation_retention__policy:=100M size}
    : ${NEO4J_dbms_memory_pagecache_size:=512M}
    : ${NEO4J_wrapper_java_additional:=-Dneo4j.ext.udc.source=docker}
    : ${NEO4J_dbms_memory_heap_initial__size:=512M}
    : ${NEO4J_dbms_memory_heap_max__size:=512M}
    : ${NEO4J_dbms_connectors_default__listen__address:=0.0.0.0}
    : ${NEO4J_dbms_connector_http_listen__address:=0.0.0.0:7474}
    : ${NEO4J_dbms_connector_https_listen__address:=0.0.0.0:7473}
    : ${NEO4J_dbms_connector_bolt_listen__address:=0.0.0.0:7687}
    : ${NEO4J_ha_host_coordination:=$(hostname):5001}
    : ${NEO4J_ha_host_data:=$(hostname):6001}
    : ${NEO4J_causal__clustering_discovery__listen__address:=0.0.0.0:5000}
    : ${NEO4J_causal__clustering_discovery__advertised__address:=$(hostname):5000}
    : ${NEO4J_causal__clustering_transaction__listen__address:=0.0.0.0:6000}
    : ${NEO4J_causal__clustering_transaction__advertised__address:=$(hostname):6000}
    : ${NEO4J_causal__clustering_raft__listen__address:=0.0.0.0:7000}
    : ${NEO4J_causal__clustering_raft__advertised__address:=$(hostname):7000}

    if [ -d /conf ]; then
        find /conf -type f -exec cp {} conf \;
    fi

    if [ -d /ssl ]; then
        NEO4J_dbms_directories_certificates="/ssl"
    fi

    if [ -d /plugins ]; then
        NEO4J_dbms_directories_plugins="/plugins"
    fi

    if [ -d /logs ]; then
        NEO4J_dbms_directories_logs="/logs"
    fi

    if [ -d /import ]; then
        NEO4J_dbms_directories_import="/import"
    fi

    if [ -d /metrics ]; then
        NEO4J_dbms_directories_metrics="/metrics"
    fi

    if [ "${NEO4J_AUTH:-}" == "none" ]; then
        NEO4J_dbms_security_auth__enabled=false
    elif [[ "${NEO4J_AUTH:-}" == neo4j/* ]]; then
        password="${NEO4J_AUTH#neo4j/}"
        if [ "${password}" == "neo4j" ]; then
            echo "Invalid value for password. It cannot be 'neo4j', which is the default."
            exit 1
        fi
        # Will exit with error if users already exist (and print a message explaining that)
        bin/neo4j-admin set-initial-password "${password}" || true
    elif [ -n "${NEO4J_AUTH:-}" ]; then
        echo "Invalid value for NEO4J_AUTH: '${NEO4J_AUTH}'"
        exit 1
    fi

    # list env variables with prefix NEO4J_ and create settings from them
    unset NEO4J_AUTH NEO4J_SHA256 NEO4J_TARBALL
    for i in $( set | grep ^NEO4J_ | awk -F'=' '{print $1}' | sort -rn ); do
        setting=$(echo ${i} | sed 's|^NEO4J_||' | sed 's|_|.|g' | sed 's|\.\.|_|g')
        value=$(echo ${!i})
        if [[ -n ${value} ]]; then
            if grep -q -F "${setting}=" conf/neo4j.conf; then
                # Remove any lines containing the setting already
                sed --in-place "/${setting}=.*/d" conf/neo4j.conf
            fi
            # Then always append setting to file
            echo "${setting}=${value}" >> conf/neo4j.conf
        fi
    done

    [ -f "${EXTENSION_SCRIPT:-}" ] && . ${EXTENSION_SCRIPT}

    exec bin/neo4j console
elif [ "$1" == "dump-config" ]; then
    if [ -d /conf ]; then
        cp --recursive conf/* /conf
    else
        echo "You must provide a /conf volume"
        exit 1
    fi
else
    exec "$@"
fi
