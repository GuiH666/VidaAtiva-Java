@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ===== Vida Ativa - compila e abre o software (Windows) =====
rem Precisa do JDK 21 instalado. Na primeira vez baixa as bibliotecas (internet).

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
if not exist lib\mysql-connector-j-8.4.0.jar (
  echo Baixando driver do MySQL...
  powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar' -OutFile 'lib\mysql-connector-j-8.4.0.jar'"
)
if not exist lib\gson-2.13.1.jar goto semlib
if not exist lib\mysql-connector-j-8.4.0.jar goto semlib

if exist out rmdir /s /q out
mkdir out
echo Compilando...
javac -encoding UTF-8 -cp "lib\*" -sourcepath src\main\java -d out src\main\java\br\com\vidaativa\ui\Main.java
if errorlevel 1 (
  echo [ERRO] Falha ao compilar.
  pause
  exit /b 1
)

echo Abrindo o Vida Ativa...
java -cp "out;lib\*" br.com.vidaativa.ui.Main
if errorlevel 1 pause
exit /b 0

:semlib
echo [ERRO] Nao consegui baixar as bibliotecas. Verifique a internet e tente de novo.
pause
exit /b 1
