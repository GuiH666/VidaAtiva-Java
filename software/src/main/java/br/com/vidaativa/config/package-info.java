/**
 * Configuração do sistema.
 * <ul>
 *   <li>{@code AppConfig}: lê o config.properties (e variáveis de ambiente) com endereço do banco, senha e porta da API.</li>
 *   <li>{@code Database}: entrega conexões JDBC com o MySQL.</li>
 * </ul>
 * Nenhuma senha fica escrita no código: tudo vem do arquivo config.properties (que não vai para o Git).
 */
package br.com.vidaativa.config;
