#!/bin/sh
GRADLE_APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
JAVA_HOME="${JAVA_HOME:-$LOCALAPPDATA/Temp/jdk17/jdk-17.0.12+7}"
CLASSPATH=$DIRNAME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
