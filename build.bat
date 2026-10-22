@echo off
rem Compiles the project with plain javac and packages build\ResumeMatch.war.
rem Needs a JDK 17+ on the PATH and these jars in lib\:
rem   mysql-connector-j-<version>.jar   (also copy it into Tomcat's lib folder)
rem   servlet-api.jar                   (from Tomcat's lib folder; used only to compile)

setlocal
cd /d "%~dp0"

if not exist lib\servlet-api.jar (
  echo lib\servlet-api.jar is missing - copy it from Tomcat's lib folder.
  exit /b 1
)

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
popd

echo Built build\ResumeMatch.war
