@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------
@setlocal

@set WRAPPER_JAR="%~dp0.mvn\wrapper\maven-wrapper.jar"
@set WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

@if not exist "%~dp0.mvn\wrapper" mkdir "%~dp0.mvn\wrapper"

@if not exist %WRAPPER_JAR% (
    @echo Downloading Maven Wrapper...
    powershell -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
)

@set MAVEN_CMD="%JAVA_HOME%\bin\java.exe"
@if "%JAVA_HOME%"=="" set MAVEN_CMD=java

%MAVEN_CMD% -jar %WRAPPER_JAR% %*
