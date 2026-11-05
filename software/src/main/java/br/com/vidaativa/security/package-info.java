/**
 * Segurança: senhas e tokens de login.
 * <ul>
 *   <li>{@code PasswordUtil}: guarda senhas com PBKDF2 + salt (nunca em texto puro).</li>
 *   <li>{@code TokenService}: cria e valida o token assinado que o site usa depois do login.</li>
 * </ul>
 */
package br.com.vidaativa.security;
