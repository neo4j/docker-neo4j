FROM alpine

RUN apk update && apk add --no-cache curl bash util-linux grep tini

ENTRYPOINT ["/sbin/tini", "-g", "--"]
CMD ["bash", "-c", "while true; do sleep 120; done"]
