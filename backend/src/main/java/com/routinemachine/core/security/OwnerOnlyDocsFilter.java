package com.routinemachine.core.security;

import com.routinemachine.core.service.OwnerAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Staged Prompt 4: the API documentation (springdoc's spec and Swagger UI) is visible to the owner only. Anyone else,
// signed in or not, gets a plain 404 with no body, never a 401 or a login prompt, so the documentation is not known to
// exist. It runs after the bearer-token filter, so the caller is already known; a malformed token was refused (401)
// before it got here, like on the other owner-only addresses. With no owner configured nobody is the owner.
//
// SecurityConfig sends exactly these patterns to the chain this filter sits in, so the chain and the filter cannot
// disagree about what counts as documentation. Not a Spring bean on purpose: a Filter bean would also be registered
// for every request, outside the security chain.
public class OwnerOnlyDocsFilter extends OncePerRequestFilter {

    public static final String[] PATTERNS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final OwnerAccount ownerAccount;

    public OwnerOnlyDocsFilter(OwnerAccount ownerAccount) {
        this.ownerAccount = ownerAccount;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        for (String pattern : PATTERNS) {
            if (MATCHER.match(pattern, path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && ownerAccount.isOwner(TokenUser.idOf(token.getToken()))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
