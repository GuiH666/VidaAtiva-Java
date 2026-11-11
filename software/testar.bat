@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ===== Vida Ativa - compila e roda os testes automaticos (Windows) =====

where javac >nul 2>nul
if errorlevel 1 (
  echo [ERRO] javac nao encontrado. Instale o JDK 21 e reabra o terminal.
  pause
  exit /b 1
)

if not exist lib mkdir lib
if not exist lib\gson-2.13.1.jar (
  echo Baixando Gson...
  powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar' -OutFile 'lib\gson-2.13.1.jar'"
)
if not exist lib\gson-2.13.1.jar (
  echo [ERRO] Nao consegui baixar o Gson. Verifique a internet.
  pause
  exit /b 1
)

if exist out-testes rmdir /s /q out-testes
mkdir out-testes
javac -encoding UTF-8 -cp "lib\*" -sourcepath "src\main\java;src\test\java" -d out-testes src\test\java\br\com\vidaativa\TestesLogica.java src\test\java\br\com\vidaativa\TestesApi.java src\test\java\br\com\vidaativa\TestesPagamentoOnline.java
if errorlevel 1 (
  echo [ERRO] Falha ao compilar.
  pause
  exit /b 1
)

echo.
echo === TestesLogica ===
java -cp "out-testes;lib\*" br.com.vidaativa.TestesLogica
echo.
echo === TestesApi ===
java -cp "out-testes;lib\*" br.com.vidaativa.TestesApi
echo.
echo === TestesPagamentoOnline ===
java -cp "out-testes;lib\*" br.com.vidaativa.TestesPagamentoOnline
echo.
pause
