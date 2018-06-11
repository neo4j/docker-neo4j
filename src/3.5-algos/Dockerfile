FROM maven:3-jdk-8-alpine
WORKDIR /neo4jjars
COPY apoc-version apoc-version
COPY graph-algos-version graph-algos-version
RUN mvn dependency:get -Dartifact="$(cat apoc-version)" -Dtransitive=false && mvn dependency:copy -Dartifact="$(cat apoc-version)" -DoutputDirectory=./downloads \
 && mvn dependency:get -Dartifact="$(cat graph-algos-version)" -Dtransitive=false && mvn dependency:copy -Dartifact="$(cat graph-algos-version)" -DoutputDirectory=./downloads


FROM %%BASE_IMAGE%%
COPY --from=0 /neo4jjars/downloads/* /plugins/
ENV NEO4J_dbms_security_procedures_unrestricted "algo.*,apoc.*"
