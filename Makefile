SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

.PHONY: all

include 2.3.4.mk 2.3.4-enterprise.mk 2.3.5.mk 2.3.5-enterprise.mk

%.mk: version.mk.template Makefile
> sed "s/%%VERSION%%/$*/g" $< >$@

.PHONY: clean

%/Dockerfile: Dockerfile.template Makefile lookup
> @mkdir -p $*
> export TAG=$*; \
    version=$$(./lookup version); \
    edition=$$(./lookup edition); \
    sha=$$(./lookup sha); \
    root=$$(./lookup root); \
    inject=$$(./lookup inject); \
    <$< sed "s|%%VERSION%%|$${version}|" \
    | sed "s|%%EDITION%%|$${edition}|" \
    | sed "s|%%DOWNLOAD_SHA%%|$${sha}|" \
    | sed "s|%%DOWNLOAD_ROOT%%|$${root}|" \
    | sed "s|%%INJECT_TARBALL%%|$${inject}|" \
    >$@

%/docker-entrypoint.sh: docker-entrypoint.sh Makefile
> @mkdir -p $*
> cp $< $@
