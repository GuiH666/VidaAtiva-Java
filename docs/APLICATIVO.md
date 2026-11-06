# Transformar o software em aplicativo (ícone na área de trabalho)

O software continua sendo o mesmo programa Java; o que muda é a forma de abrir. Em vez de `rodar.bat` (compila e abre pelo
terminal), você gera uma pasta **Vida Ativa** com o `Vida Ativa.exe`, que:

- abre com **duplo clique**, sem janela preta de terminal;
- usa o **logo da academia** como ícone (arquivo `.exe`, atalho, janela e barra de tarefas);
- **leva o Java dentro da pasta**: o computador da recepção não precisa ter o Java instalado;
- sobe a API do site junto, como sempre (`api.autostart=true`).

## Gerar o aplicativo (uma vez, no computador de desenvolvimento)

Pré-requisito: **JDK 21** (não o JRE) instalado, com `javac` e `jpackage` no PATH.

1. Dê dois cliques em `software\empacotar.bat`. Na primeira vez ele baixa o Gson e o driver do MySQL (internet).
2. Espere 1 a 2 minutos. No fim ele cria `software\dist\Vida Ativa\` e o atalho **Vida Ativa** na área de trabalho e no menu Iniciar.
3. O MySQL precisa estar ligado para o login funcionar.

Se existir `software\config.properties`, ele é copiado para dentro da pasta do aplicativo. Se não existir, copie
`config.properties.example` (que também vai na pasta) para `config.properties` e preencha a senha do MySQL.

## Instalar em outro computador (recepção, por exemplo)

1. Copie a pasta `dist\Vida Ativa` **inteira** (pendrive ou rede) para o outro computador, de preferência para uma pasta sua
   (por exemplo `C:\VidaAtiva\`). Não coloque em `C:\Program Files`, porque aí editar o `config.properties` exigiria administrador.
2. Dentro da pasta, dê dois cliques em **Criar atalho na area de trabalho.bat**.
3. Ajuste o `config.properties` para apontar ao MySQL certo (veja abaixo).

## Onde o programa procura o config.properties

Na ordem, o primeiro que existir vence: pasta atual, `software\`, **a pasta do aplicativo (ao lado do .exe)** e
`%USERPROFILE%\VidaAtiva\config.properties`. As variáveis de ambiente `VIDAATIVA_*` continuam valendo acima de tudo.
Esse arquivo tem a senha do banco: não envie a pasta do aplicativo para terceiros nem coloque no Git (o `dist/` já está no `.gitignore`).

## Atualizar para uma nova versão

Rode `empacotar.bat` de novo e copie a nova pasta `dist\Vida Ativa` por cima da antiga (mantenha o seu `config.properties`).

## Instalador (.exe com "Avançar, Avançar")

Não é necessário, mas o `jpackage` também gera um instalador. Ele exige o **WiX Toolset 3** instalado. Depois de rodar o
`empacotar.bat`, execute no terminal, dentro de `software`:

```
jpackage --type exe --name "Vida Ativa" --app-version 1.0.0 --vendor "Os TraKinEirOs" --input build-app --main-jar vida-ativa.jar ^
  --main-class br.com.vidaativa.ui.Main --icon empacotamento\vida-ativa.ico --dest dist ^
  --java-options "-Dfile.encoding=UTF-8" --win-shortcut --win-menu --win-dir-chooser
```

O instalador instala em `C:\Program Files\Vida Ativa` por padrão; nesse caso coloque o `config.properties` em
`%USERPROFILE%\VidaAtiva\`.

## O que foi feito no código

| Arquivo | Para quê |
|---|---|
| `software/empacotar.bat` | compila, monta o `.jar` e chama o `jpackage` (gera a pasta com o Java embutido), copia o config e cria o atalho |
| `software/empacotamento/criar-atalho.bat` | cria o atalho com o ícone na área de trabalho e no menu Iniciar; vai dentro da pasta do aplicativo |
| `software/empacotamento/vida-ativa.ico` | ícone do `.exe` e do atalho (16 a 256 px, feito do logo do site) |
| `software/src/main/resources/icone/vida-ativa.png` | ícone das janelas (lido por `ui/IconeApp.java`) |
| `config/AppConfig.java` | passou a procurar o `config.properties` também ao lado do aplicativo e em `~/VidaAtiva` |

## Limites conhecidos

- O aplicativo gerado é **só para Windows** quando gerado no Windows (o `jpackage` não gera `.exe` a partir de Linux/macOS).
- Se o Windows SmartScreen avisar "editor desconhecido" na primeira vez, é porque o `.exe` não tem assinatura digital paga.
  Clique em "Mais informações > Executar assim mesmo".
- Se o programa não abrir (sem mensagem), rode `rodar.bat` para ver o erro no terminal: o `.exe` não mostra console.
