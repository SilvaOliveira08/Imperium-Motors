package br.com.dataflowauto.comum.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Recusa o envio do formulário de login enquanto o IP estiver bloqueado para aquele usuário, antes
 * mesmo de conferir a senha: senão o bloqueio não adiantaria, porque a senha certa ainda seria aceita.
 * Não é um {@code @Component} de propósito: só entra na cadeia do Spring Security (ver {@link SegurancaConfig}).
 */
class FiltroBloqueioLogin extends OncePerRequestFilter {

    private final ControleTentativasLogin tentativas;

    FiltroBloqueioLogin(ControleTentativasLogin tentativas) {
        this.tentativas = tentativas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadeia)
            throws ServletException, IOException {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && SegurancaConfig.PAGINA_LOGIN.equals(caminho)
                && tentativas.bloqueado(request.getRemoteAddr(), request.getParameter("username"))) {
            response.sendRedirect(request.getContextPath() + SegurancaConfig.PAGINA_LOGIN + "?bloqueado");
            return;
        }
        cadeia.doFilter(request, response);
    }
}
