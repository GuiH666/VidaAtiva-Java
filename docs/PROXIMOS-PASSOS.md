# Próximos passos

Lista de melhorias, da que mais importa para a que menos importa. Itens com *(achado na revisão)* foram
encontrados lendo o código e ainda **não** foram corrigidos.

## 1. Visual e usabilidade do software (pedido do grupo)

- [ ] Telas de cadastro (aluno, funcionário, professor) mais explicativas: agrupar campos, textos de ajuda,
      mensagens de erro ao lado do campo em vez de só no aviso.
- [ ] Máscaras de CPF, telefone e data durante a digitação.
- [ ] Ícones nos botões e menu; destacar visualmente o que é obrigatório.
- [ ] Tabelas com ordenação por coluna.
- [ ] Revisar a tela de ficha de treino (hoje é grande; talvez dividir em passos).
- [ ] Visual do site: revisar páginas Home, Planos e Área do Aluno (imagens da academia, depoimentos, contato).

## 2. Testar no banco real (ainda não foi feito de ponta a ponta)

As regras e a API têm teste automático, mas **DAOs e telas que dependem do banco nunca rodaram contra um MySQL em
ambiente de teste automatizado**. Percorra cada tela com o banco de demonstração e anote o que quebrar:
cadastro de aluno/funcionário/professor, geração de mensalidades, registrar pagamento, ficha completa, avaliação.

## 3. Possíveis problemas encontrados na revisão do código

- [x] *(corrigido na v2.1, RFC-001)* `Validacao.documento` agora confere os dígitos do CPF (documento de 11 dígitos) e grava sempre
      com máscara `000.000.000-00`, então o mesmo CPF com ou sem pontuação cai na chave UNIQUE. Limites: RG com exatamente 11 dígitos
      seria tratado como CPF; alunos antigos gravados sem máscara não colidem com os novos (veja `OPERACAO.md`, seção 5).
- [ ] *(achado na revisão)* `FichaTreinoDAO.salvar`: o UPDATE não grava `professor_id` (a ficha continua no nome de quem criou).
      Provavelmente intencional, confirmar.
- [ ] *(achado na revisão)* `FuncionarioDAO.definirAtivo` não impede desativar o gerente (só o `PessoalPanel` bloqueia).
- [ ] *(achado na revisão)* `PagamentoDAO.inserirMensalidade` trata qualquer violação de integridade como "já existe",
      o que esconderia também uma violação de chave estrangeira.
- [ ] *(achado na revisão)* `PagamentoDAO.cancelar` mostra "só é possível cancelar cobranças não pagas" até quando o id não existe.
- [ ] *(achado na revisão)* Listagens de pagamentos e o resumo fazem `UPDATE` (marcar atrasados) dentro de uma leitura.
- [ ] *(achado na revisão)* `AlunoDAO.atualizar` não avisa se o id não existe; regras "ficha só para Plus/Premium" e
      "avaliação só para Premium" estão só nas telas, não nos DAOs nem na API.
- [ ] *(redesenho v3)* `TreinoPanel`: se o dia da semana gravado não for um dos sete dias, ele aparece sem seleção e é gravado como "sem dia" ao salvar.
- [ ] *(achado na revisão)* `LoginFrame` e `ResumoPanel` consultam o banco na thread da interface (a tela pode "travar"
      por instantes se o banco demorar). Usar `SwingWorker`.
- [ ] *(achado na revisão)* Token da API não pode ser revogado (trocar a senha não invalida; vale 8 h). Limite de login é por IP
      e o mapa de falhas nunca é limpo; `/senha` não tem limite de tentativas.
- [ ] *(achado na revisão)* `PasswordUtil.precisaAtualizar` só olha o prefixo: se o número de iterações aumentar,
      hashes antigos não são regravados.
- [ ] *(achado na revisão)* `AppConfig`: se `-Dvidaativa.config` apontar para arquivo inexistente, usa os padrões em silêncio.
- [ ] *(achado na revisão)* Preço de upgrade TotalPass está repetido em `Precificacao` e em `site/pages/planos.html`.

## 4. Funcionalidades que ainda não existem

- [ ] Pagamento online (hoje o aluno só consulta; recebimento é feito na recepção).
- [ ] Recuperação de senha do aluno por e-mail.
- [ ] Relatórios: o dashboard do gerente já mostra inadimplência e alunos por plano; faltam exportar (PDF/CSV) e a evolução de avaliações.
- [ ] Cadastro de planos pelo software (hoje só direto no banco).
- [ ] Agendamento de avaliações / aulas.

## 5. Para publicar de verdade

- [ ] API fora do computador local, com HTTPS; `api.cors.origin` com o endereço exato do site.
- [ ] `api.secret` forte e **trocar todas as senhas de demonstração** (`seed.sql` não deve ir para produção).
- [ ] Backup automático do banco.
- [ ] Pool de conexões (hoje cada operação abre uma conexão).

## Redesenho visual (v3): o que ainda precisa de teste com banco real

- [ ] As telas foram conferidas por capturas com dados de exemplo (sem MySQL no ambiente de desenvolvimento). Abra no seu
      ambiente: Painel do gerente, Alunos, Pagamentos, Fichas (criar, editar, copiar, consulta) e confira com dados reais.
- [ ] Dashboard: "cadastro" usa a data de início do aluno. Se o cadastro puder ser retroativo, vale criar uma coluna `criado_em`.
- [ ] O dashboard carrega tudo em memória (alunos + cobranças). Para milhares de alunos, trocar por consultas agregadas em SQL.
- [ ] As fontes seguem a lista Segoe UI, Inter, SF Pro Text, Helvetica Neue, Roboto, Noto Sans; sem nenhuma, usa a padrão do Java.
