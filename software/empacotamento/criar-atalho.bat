@echo off
chcp 65001 >nul
setlocal
rem ===== Vida Ativa - cria o icone na area de trabalho e no menu Iniciar =====
rem Este arquivo fica DENTRO da pasta do aplicativo, ao lado do "Vida Ativa.exe". Pode ser rodado de novo quando quiser
rem (por exemplo, depois de copiar a pasta para outro computador ou de mudar a pasta de lugar).
rem Com o argumento /silencioso, nao pede para apertar uma tecla no final.

set "VA_EXE=%~dp0Vida Ativa.exe"
if not exist "%VA_EXE%" (
  echo [ERRO] "Vida Ativa.exe" nao esta nesta pasta. Rode este arquivo de dentro da pasta do aplicativo.
  pause
  exit /b 1
)
set "VA_PASTA=%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$sh = New-Object -ComObject WScript.Shell;" ^
  "foreach ($pasta in @('Desktop','Programs')) {" ^
  "  $destino = [Environment]::GetFolderPath($pasta);" ^
  "  $atalho = $sh.CreateShortcut((Join-Path $destino 'Vida Ativa.lnk'));" ^
  "  $atalho.TargetPath = $env:VA_EXE;" ^
  "  $atalho.WorkingDirectory = $env:VA_PASTA;" ^
  "  $atalho.IconLocation = $env:VA_EXE + ',0';" ^
  "  $atalho.Description = 'Vida Ativa - Software de Gestao da Academia';" ^
  "  $atalho.Save();" ^
  "}"
if errorlevel 1 (
  echo [ERRO] Nao foi possivel criar o atalho. Voce ainda pode abrir "Vida Ativa.exe" direto desta pasta.
  if /i not "%~1"=="/silencioso" pause
  exit /b 1
)
echo Atalho "Vida Ativa" criado na area de trabalho e no menu Iniciar.
if /i not "%~1"=="/silencioso" pause
exit /b 0
