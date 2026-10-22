#!/bin/bash
# Compiles the project with plain javac and packages build/ResumeMatch.war.
# Needs a JDK 17+ on the PATH and these jars in lib/:
#   mysql-connector-j-<version>.jar   (also copy it into Tomcat's lib folder)
#   servlet-api.jar                   (from Tomcat's lib folder; used only to compile)
#
# Usage:
#   ./build.sh                build the war
#   ./build.sh test           compile main + test into build/test-classes and run the tests
#   ./build.sh deploy         build the war, then copy it into $CATALINA_HOME/webapps
#   ./build.sh test deploy    build, run the tests, deploy only if they pass
# Tomcat is never stopped or started; it redeploys a changed war by itself.

set -e
cd "$(dirname "$0")"

DO_TEST=0
DO_DEPLOY=0
for arg in "$@"; do
  case "$arg" in
    test)   DO_TEST=1 ;;
    deploy) DO_DEPLOY=1 ;;
    *) echo "Unknown argument: $arg (use: test, deploy)"; exit 1 ;;
  esac
done

# javac.exe on Windows (Git Bash) wants ';' between classpath entries, Linux/macOS want ':'.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *)                    SEP=':' ;;
esac
CLASSPATH_JARS=$(ls lib/*.jar | tr '\n' "$SEP")
CLASSPATH_JARS="${CLASSPATH_JARS%"$SEP"}"
if ! ls lib/servlet-api.jar >/dev/null 2>&1; then
  echo "lib/servlet-api.jar is missing - copy it from Tomcat's lib folder."; exit 1
fi

# "test" on its own only compiles and runs the tests; the war is not rebuilt.
if [ $DO_TEST -eq 1 ] && [ $DO_DEPLOY -eq 0 ]; then
  BUILD_WAR=0
else
  BUILD_WAR=1
fi

if [ $BUILD_WAR -eq 1 ]; then
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
fi

if [ $DO_TEST -eq 1 ]; then
  echo "Compiling tests..."
  rm -rf build/test-classes
  mkdir -p build/test-classes
  javac --release 17 -Xlint:all -encoding UTF-8 -cp "$CLASSPATH_JARS" \
        -d build/test-classes $(find src/main/java test -name '*.java')

  echo "Running tests..."
  java -cp "build/test-classes$SEP$CLASSPATH_JARS" com.resumematch.TestRunner
fi

if [ $DO_DEPLOY -eq 1 ]; then
  TOMCAT="${CATALINA_HOME:-C:/Program Files/Apache Software Foundation/Tomcat 9.0}"
  if [ ! -d "$TOMCAT/webapps" ]; then
    echo "Tomcat webapps folder not found: $TOMCAT/webapps (set CATALINA_HOME)"; exit 1
  fi
  cp build/ResumeMatch.war "$TOMCAT/webapps/ResumeMatch.war"
  echo "Copied build/ResumeMatch.war to $TOMCAT/webapps"
  echo "Tomcat redeploys the new war automatically; no restart is needed."
fi
