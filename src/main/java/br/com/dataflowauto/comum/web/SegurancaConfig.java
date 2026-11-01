package br.com.dataflowauto.comum.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * A loja (catálogo, página do carro e formulário de contato) é pública. Todo o resto
 * (painel, estoque, planilhas, fichas e histórico) exige login da equipe da loja.
 */
@Configuration
public class SegurancaConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SegurancaConfig.class);
    private static final String SENHA_PADRAO = "dataflow123";
    private static final int TAMANHO_MINIMO_SENHA = 10;
    static final String PAGINA_LOGIN = "/entrar";

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, ControleTentativasLogin tentativas) throws Exception {
        // Carrega o token CSRF já no início da requisição: em páginas grandes a resposta começa a ser
        // enviada antes de o Thymeleaf chegar ao formulário, e aí não daria mais para criar a sessão.
        CsrfTokenRequestAttributeHandler tokenCsrf = new CsrfTokenRequestAttributeHandler();
        tokenCsrf.setCsrfRequestAttributeName(null);
        http.csrf(csrf -> csrf.csrfTokenRequestHandler(tokenCsrf))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/", "/loja/**", "/fotos/**", "/css/**", "/js/**", "/img/**",
                                "/entrar", "/error", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                // o bloqueio por excesso de tentativas é conferido antes da senha
                .addFilterBefore(new FiltroBloqueioLogin(tentativas), UsernamePasswordAuthenticationFilter.class)
                .formLogin(login -> login
                        .loginPage(PAGINA_LOGIN)
                        .successHandler(loginAceito(tentativas))
                        .failureHandler(loginRecusado(tentativas))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/sair")
                        .logoutSuccessUrl("/?saiu"))
                .headers(cabecalhos -> cabecalhos
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                                        + "frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }

    /** Zera as falhas daquele IP e segue para a página pedida antes do login (ou o painel). */
    private static AuthenticationSuccessHandler loginAceito(ControleTentativasLogin tentativas) {
        SavedRequestAwareAuthenticationSuccessHandler padrao = new SavedRequestAwareAuthenticationSuccessHandler();
        padrao.setDefaultTargetUrl("/painel");
        return (request, response, autenticacao) -> {
            tentativas.registrarSucesso(request.getRemoteAddr(), autenticacao.getName());
            padrao.onAuthenticationSuccess(request, response, autenticacao);
        };
    }

    /** Conta a falha, deixa registrado no log com IP e usuário e avisa na tela quando o bloqueio começa. */
    private static AuthenticationFailureHandler loginRecusado(ControleTentativasLogin tentativas) {
        return (request, response, erro) -> {
            String ip = request.getRemoteAddr();
            String usuario = request.getParameter("username");
            int falhas = tentativas.registrarFalha(ip, usuario);
            boolean bloqueou = tentativas.bloqueado(ip, usuario);
            LOG.warn("Login recusado para o usuário \"{}\" a partir de {} ({} falha(s) seguida(s){})", paraLog(usuario), ip,
                    falhas, bloqueou ? "; bloqueado por " + tentativas.getJanela().toMinutes() + " min" : "");
            response.sendRedirect(request.getContextPath() + PAGINA_LOGIN + (bloqueou ? "?bloqueado" : "?error"));
        };
    }

    /** O usuário vem do formulário: sem quebras de linha (não dá para forjar linhas no log) e com tamanho limitado. */
    static String paraLog(String usuario) {
        if (usuario == null) {
            return "";
        }
        String limpo = usuario.replaceAll("\\p{Cntrl}", "_");
        return limpo.length() > 60 ? limpo.substring(0, 60) + "…" : limpo;
    }

    @Bean
    PasswordEncoder codificadorSenha() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(@Value("${dataflow.admin.usuario:admin}") String usuario,
                                @Value("${dataflow.admin.senha:}") String senha,
                                @Value("${dataflow.admin.senha-padrao-permitida:false}") boolean senhaPadraoPermitida,
                                PasswordEncoder codificador) {
        validarSenha(senha, senhaPadraoPermitida);
        return new InMemoryUserDetailsManager(User.withUsername(usuario)
                .password(codificador.encode(senha))
                .roles("ADMIN")
                .build());
    }

    /**
     * Impede que o painel suba sem senha ou com a senha publicada no README. A senha padrão só é
     * aceita onde ela é explicitamente permitida (perfil local e testes); fora disso o sistema nem inicia.
     */
    static void validarSenha(String senha, boolean senhaPadraoPermitida) {
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException("Defina a senha do painel na variável DATAFLOW_ADMIN_SENHA "
                    + "(ou rode com o perfil local para usar a senha de desenvolvimento).");
        }
        if (SENHA_PADRAO.equals(senha)) {
            if (!senhaPadraoPermitida) {
                throw new IllegalStateException("A senha padrão do painel só é aceita no perfil local. "
                        + "Defina DATAFLOW_ADMIN_SENHA com outra senha antes de publicar o site.");
            }
            LOG.warn("Usando a senha padrão do painel (permitida só para desenvolvimento).");
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalStateException("A senha do painel precisa ter pelo menos "
                    + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
