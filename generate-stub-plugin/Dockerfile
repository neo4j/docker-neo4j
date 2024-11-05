FROM gradle:7-jdk11
WORKDIR /testplugin
COPY ./*.kts ./ExampleNeo4jPlugin.java /testplugin/
RUN mkdir -p /testplugin/src/main/java/testplugins/ && \
    mkdir -p /testplugin/build && \
    ln -s -T /output /testplugin/build/libs && \
    mv /testplugin/ExampleNeo4jPlugin.java /testplugin/src/main/java/testplugins/ && \
    chmod a+rw -R /testplugin
ENV NEO4JVERSION=4.4.38

VOLUME /output
CMD gradle jar -Pversion=$NEO4JVERSION
