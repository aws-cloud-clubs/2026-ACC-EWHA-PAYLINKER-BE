package com.paylinker.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.auth.LinkSession;
import com.paylinker.api.auth.LinkSessionAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class LinkSessionAuthFilter extends OncePerRequestFilter {

    private static final String LS_PREFIX = "ls_";
    private static final String REDIS_KEY_PREFIX = "paylinker:linksession:";
    private static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            if (token.startsWith(LS_PREFIX)) {
                String json = redis.opsForValue().get(REDIS_KEY_PREFIX + token);
                if (json != null) {
                    LinkSession session = mapper.readValue(json, LinkSession.class);
                    if (session.expiresAt().isAfter(Instant.now())) {
                        LinkSessionAuthentication auth = new LinkSessionAuthentication(session);
                        auth.setAuthenticated(true);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        chain.doFilter(req, res);
    }
}
