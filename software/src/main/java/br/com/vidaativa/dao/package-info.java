/**
 * DAOs (Data Access Object): únicas classes que falam SQL com o MySQL.
 * Cada tabela principal tem o seu DAO (AlunoDAO, PagamentoDAO, ...). Telas e API nunca montam SQL:
 * chamam um DAO. {@code Sql} reúne pequenos auxiliares (valores nulos, mensagens de erro amigáveis).
 */
package br.com.vidaativa.dao;
