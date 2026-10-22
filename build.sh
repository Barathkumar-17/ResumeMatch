#!/bin/bash
# Compiles the project with plain javac and packages build/ResumeMatch.war.
# Needs a JDK 17+ on the PATH and these jars in lib/:
#   mysql-connector-j-<version>.jar   (also copy it into Tomcat's lib folder)
#   servlet-api.jar                   (from Tomcat's lib folder; used only to compile)

set -e
cd "$(dirname "$0")"

CLASSPATH_JARS=$(ls lib/*.jar | tr '\n' ':')
if ! ls lib/servlet-api.jar >/dev/null 2>&1; then
  echo "lib/servlet-api.jar is missing - copy it from Tomcat's lib folder."; exit 1
fi

rm -rf build
mkdir -p build/war/WEB-INF/classes

echo "Compiling..."
javac --release 17 -Xlint:all -encoding UTF-8 -cp "$CLASSPATH_JARS" \
      -d build/war/WEB-INF/classes $(find src/main/java -name '*.java')

echo "Packaging..."
cp -r src/main/webapp/. build/war/
if [ -f src/main/resources/db.properties ]; then
  cp src/main/resources/db.properties build/war/WEB-INF/classes/
else
  echo "Note: src/main/resources/db.properties not found - the war will use the built-in defaults."
fi
find build/war -name .gitkeep -delete
(cd build/war && jar -cf ../ResumeMatch.war .)

echo "Built build/ResumeMatch.war"
