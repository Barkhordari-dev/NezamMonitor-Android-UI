#!/bin/sh
GRADLE_APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
if [ -z "$JAVA_HOME" ]; then
    JAVA_CMD=$(which java 2>/dev/null)
    if [ -n "$JAVA_CMD" ]; then
        JAVA_HOME=$(dirname $(dirname $(readlink -f "$JAVA_CMD" 2>/dev/null || echo "$JAVA_CMD")))
    else
        JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    fi
fi
CLASSPATH=$DIRNAME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
