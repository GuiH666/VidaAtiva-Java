@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ===== Vida Ativa - transforma o software em APLICATIVO (Windows) =====
rem Gera a pasta  dist\Vida Ativa\  com o "Vida Ativa.exe" (icone do logo), que abre SEM precisar do Java instalado,
rem e cria o atalho na area de trabalho. Precisa do JDK 21 (com jpackage) apenas NESTE computador, para gerar o aplicativo.
rem Para usar em outro computador: copie a pasta "dist\Vida Ativa" inteira e rode "Criar atalho na area de trabalho.bat" dentro dela.

where javac >nul 2>nul
if errorlevel 1 (
  echo [ERRO] javac nao encontrado. Instale o JDK 21 ^(nao o JRE^) e reabra o terminal.
  pause
  exit /b 1
)
where jpackage >nul 2>nul
if errorlevel 1 (
  echo [ERRO] jpackage nao encontrado. Ele vem no JDK 21; confira se o JDK instalado e o 21 e se a pasta bin dele esta no PATH.
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

echo [1/4] Compilando...
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 -cp "lib\*" -sourcepath src\main\java -d out src\main\java\br\com\vidaativa\ui\Main.java
if errorlevel 1 goto erro

echo [2/4] Montando o pacote...
if exist build-app rmdir /s /q build-app
mkdir build-app
jar --create --file build-app\vida-ativa.jar --main-class br.com.vidaativa.ui.Main -C out . -C src\main\resources .
if errorlevel 1 goto erro
copy /y lib\gson-2.13.1.jar build-app\ >nul
copy /y lib\mysql-connector-j-8.4.0.jar build-app\ >nul

echo [3/4] Gerando o aplicativo ^(leva 1 a 2 minutos^)...
if exist "dist\Vida Ativa" rmdir /s /q "dist\Vida Ativa"
jpackage --type app-image --name "Vida Ativa" --app-version 1.0.0 --vendor "Os TraKinEirOs" ^
  --description "Vida Ativa - Software de Gestao da Academia" ^
  --input build-app --main-jar vida-ativa.jar --main-class br.com.vidaativa.ui.Main ^
  --icon empacotamento\vida-ativa.ico --dest dist ^
  --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto erro
if not exist "dist\Vida Ativa\Vida Ativa.exe" goto erro

copy /y empacotamento\criar-atalho.bat "dist\Vida Ativa\Criar atalho na area de trabalho.bat" >nul
rem Leva junto a configuracao do banco, se ja existir uma (tem senha: nao envie essa pasta para terceiros).
if exist config.properties (
  copy /y config.properties "dist\Vida Ativa\config.properties" >nul
  echo      config.properties copiado para a pasta do aplicativo.
) else (
  copy /y config.properties.example "dist\Vida Ativa\config.properties.example" >nul
  echo      ATENCAO: nao achei config.properties. Dentro de "dist\Vida Ativa", copie config.properties.example para config.properties e preencha a senha do MySQL.
)

echo [4/4] Criando o atalho na area de trabalho...
call "dist\Vida Ativa\Criar atalho na area de trabalho.bat" /silencioso

echo.
echo Pronto! Aplicativo em: %CD%\dist\Vida Ativa
echo Abra pelo icone "Vida Ativa" na area de trabalho. O MySQL precisa estar ligado.
echo.
pause
exit /b 0

:semlib
echo [ERRO] Nao consegui baixar as bibliotecas. Verifique a internet e tente de novo.
pause
exit /b 1

:erro
echo [ERRO] Falha ao gerar o aplicativo. Veja a mensagem acima.
pause
exit /b 1
