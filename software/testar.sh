#!/usr/bin/env bash
# Vida Ativa - compila e roda os testes automáticos (Linux/macOS). Precisa do JDK 21.
set -e
cd "$(dirname "$0")"
mkdir -p lib
[ -f lib/gson-2.13.1.jar ] || curl -L -o lib/gson-2.13.1.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar
rm -rf out-testes && mkdir out-testes
javac -encoding UTF-8 -cp "lib/*" -sourcepath "src/main/java:src/test/java" -d out-testes \
  src/test/java/br/com/vidaativa/TestesLogica.java src/test/java/br/com/vidaativa/TestesApi.java src/test/java/br/com/vidaativa/TestesPagamentoOnline.java
echo "=== TestesLogica ===";  java -cp "out-testes:lib/*" br.com.vidaativa.TestesLogica
echo "=== TestesApi ===";     java -cp "out-testes:lib/*" br.com.vidaativa.TestesApi
echo "=== TestesPagamentoOnline ==="; java -cp "out-testes:lib/*" br.com.vidaativa.TestesPagamentoOnline
