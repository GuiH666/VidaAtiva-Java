#!/usr/bin/env bash
# Vida Ativa - compila e abre o software (Linux/macOS). Precisa do JDK 21.
set -e
cd "$(dirname "$0")"
mkdir -p lib
[ -f lib/gson-2.13.1.jar ] || curl -L -o lib/gson-2.13.1.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar
[ -f lib/mysql-connector-j-8.4.0.jar ] || curl -L -o lib/mysql-connector-j-8.4.0.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
rm -rf out && mkdir out
javac -encoding UTF-8 -cp "lib/*" -sourcepath src/main/java -d out src/main/java/br/com/vidaativa/ui/Main.java
java -cp "out:src/main/resources:lib/*" br.com.vidaativa.ui.Main
