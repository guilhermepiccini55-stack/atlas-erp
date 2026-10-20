package com.atlas.erp.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê "Authorization: Bearer ...", valida o JWT e autentica a requisição.
 * Instanciado em SecurityConfig (não é @Component) para não ser registrado duas vezes no container.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER.length()).trim();
            try {
                String username = jwtService.extractUsername(token);
                long tokenVersion = jwtService.extractCredentialVersion(token);
                UserDetails details = userDetailsService.loadUserByUsername(username);
                if (details instanceof AtlasUserDetails atlasDetails
                        && atlasDetails.getCredentialVersion() == tokenVersion
                        && details.isEnabled()) {
                    atlasDetails.eraseCredentials();
                    var authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("JWT rejeitado: versão da credencial inválida");
                }
            } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
                // Token inválido/expirado ou usuário removido: segue sem autenticar (o entry point responde 401).
                log.debug("JWT rejeitado: token inválido ou usuário não autenticável");
            }
        }
        chain.doFilter(request, response);
    }
}
