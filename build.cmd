@echo off
rem Minecraft 26.x needs JDK 25 to build; use the portable one when it is there.
if exist "%USERPROFILE%\.jdks\jdk-25.0.4.1+1" set "JAVA_HOME=%USERPROFILE%\.jdks\jdk-25.0.4.1+1"
rem Loom cannot handle the UNC path of the NAS, so Gradle keeps its project cache locally.
call "%~dp0gradlew.bat" --project-cache-dir "%USERPROFILE%\.gradle\twitchplays-cache" %*