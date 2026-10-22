@echo off
rem Compiles the project with plain javac and packages build\ResumeMatch.war.
rem Needs a JDK 17+ on the PATH and these jars in lib\:
rem   mysql-connector-j-<version>.jar   (also copy it into Tomcat's lib folder)
rem   servlet-api.jar                   (from Tomcat's lib folder; used only to compile)
rem
rem Usage:
rem   build.bat                build the war
rem   build.bat test           compile main + test into build\test-classes and run the tests
rem   build.bat deploy         build the war, then copy it into %CATALINA_HOME%\webapps
rem   build.bat test deploy    build, run the tests, deploy only if they pass
rem Tomcat is never stopped or started; it redeploys a changed war by itself.

setlocal
cd /d "%~dp0"

set DO_TEST=0
set DO_DEPLOY=0
:args
if "%~1"=="" goto argsdone
if /i "%~1"=="test" (set DO_TEST=1) else if /i "%~1"=="deploy" (set DO_DEPLOY=1) else (
  echo Unknown argument: %~1 ^(use: test, deploy^)
  exit /b 1
)
shift
goto args
:argsdone

if not exist lib\servlet-api.jar (
  echo lib\servlet-api.jar is missing - copy it from Tomcat's lib folder.
  exit /b 1
)

rem "test" on its own only compiles and runs the tests; the war is not rebuilt.
set BUILD_WAR=1
if %DO_TEST%==1 if %DO_DEPLOY%==0 set BUILD_WAR=0

if %BUILD_WAR%==0 goto tests

if exist build rmdir /s /q build
mkdir build\war\WEB-INF\classes

echo Compiling...
dir /s /b src\main\java\*.java > build\sources.txt
javac --release 17 -Xlint:all -encoding UTF-8 -cp "lib\*" -d build\war\WEB-INF\classes @build\sources.txt
if errorlevel 1 exit /b 1

echo Packaging...
xcopy /e /i /q /y src\main\webapp build\war >nul
if exist src\main\resources\db.properties (
  copy /y src\main\resources\db.properties build\war\WEB-INF\classes >nul
) else (
  echo Note: src\main\resources\db.properties not found - the war will use the built-in defaults.
)
del /s /q build\war\.gitkeep >nul 2>&1
pushd build\war
jar -cf ..\ResumeMatch.war .
if errorlevel 1 (
  popd
  exit /b 1
)
popd

echo Built build\ResumeMatch.war

:tests
if %DO_TEST%==0 goto deploy

echo Compiling tests...
if exist build\test-classes rmdir /s /q build\test-classes
mkdir build\test-classes
dir /s /b src\main\java\*.java test\*.java > build\test-sources.txt
javac --release 17 -Xlint:all -encoding UTF-8 -cp "lib\*" -d build\test-classes @build\test-sources.txt
if errorlevel 1 exit /b 1

echo Running tests...
java -cp "build\test-classes;lib\*" com.resumematch.TestRunner
if errorlevel 1 exit /b 1

:deploy
if %DO_DEPLOY%==0 exit /b 0

set "TOMCAT=%CATALINA_HOME%"
if "%TOMCAT%"=="" set "TOMCAT=C:\Program Files\Apache Software Foundation\Tomcat 9.0"
if not exist "%TOMCAT%\webapps" (
  echo Tomcat webapps folder not found: %TOMCAT%\webapps ^(set CATALINA_HOME^)
  exit /b 1
)
copy /y build\ResumeMatch.war "%TOMCAT%\webapps\ResumeMatch.war" >nul
if errorlevel 1 (
  echo Could not copy the war into %TOMCAT%\webapps
  exit /b 1
)
echo Copied build\ResumeMatch.war to %TOMCAT%\webapps
echo Tomcat redeploys the new war automatically; no restart is needed.
