# ResumeMatch container image: Tomcat 9 + the ResumeMatch war.
# Build:  docker build -t resumematch .
# Run it together with MySQL using docker-compose.yml (docker compose up --build).

# ---------- build stage: compile with plain javac, package the war ----------
FROM tomcat:9.0-jdk17-temurin AS build
ARG MYSQL_CONNECTOR_VERSION=26.10.0
WORKDIR /src
COPY src/main ./src/main

# The MySQL JDBC driver is the only extra jar; it is checked against Maven Central's SHA-1.
RUN set -e; \
    base="https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}"; \
    jar="mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"; \
    curl -fsSL -o "/tmp/${jar}" "${base}/${jar}"; \
    echo "$(curl -fsSL "${base}/${jar}.sha1")  /tmp/${jar}" | sha1sum -c -; \
    mv "/tmp/${jar}" /tmp/mysql-connector-j.jar

RUN set -e; \
    mkdir -p war/WEB-INF/classes; \
    javac --release 17 -Xlint:all -encoding UTF-8 \
          -cp "/usr/local/tomcat/lib/servlet-api.jar:/tmp/mysql-connector-j.jar" \
          -d war/WEB-INF/classes $(find src/main/java -name '*.java'); \
    cp -r src/main/webapp/. war/; \
    find war -name .gitkeep -delete; \
    (cd war && jar -cf /tmp/ResumeMatch.war .)

# ---------- runtime stage ----------
FROM tomcat:9.0-jre17-temurin
COPY --from=build /tmp/mysql-connector-j.jar /usr/local/tomcat/lib/
COPY --from=build /tmp/ResumeMatch.war /usr/local/tomcat/webapps/ResumeMatch.war

# Behind a hosting proxy, take the client address from X-Forwarded-For (only when the request
# comes from a private-network proxy), so the sign-in limit counts real visitors, not the proxy.
# The site root redirects to the app.
RUN set -e; \
    sed -i 's#</Host>#  <Valve className="org.apache.catalina.valves.RemoteIpValve" protocolHeader="X-Forwarded-Proto"/>\n      </Host>#' conf/server.xml; \
    mkdir -p webapps/ROOT resumes; \
    printf '<%% response.sendRedirect("/ResumeMatch/"); %%>\n' > webapps/ROOT/index.jsp; \
    useradd --system --no-create-home --home-dir /usr/local/tomcat resumematch; \
    chown -R resumematch:resumematch logs temp work webapps resumes conf

USER resumematch
VOLUME ["/usr/local/tomcat/resumes"]
EXPOSE 8080
CMD ["catalina.sh", "run"]
