package com.pesachain.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class CurrentAccountFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityContextRepository contexts;
    public CurrentAccountFilter(AuthService auth, SecurityContextRepository contexts) { this.auth=auth; this.contexts=contexts; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var context=SecurityContextHolder.getContext();
        if(context.getAuthentication()!=null && context.getAuthentication().getPrincipal() instanceof UserPrincipal old) {
            UserPrincipal fresh=auth.getAccount(old.id());
            if(!fresh.isEnabled()) {
                var session=request.getSession(false); if(session!=null) session.invalidate();
                SecurityContextHolder.clearContext(); contexts.saveContext(SecurityContextHolder.createEmptyContext(),request,response);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Account is inactive."); return;
            }
            var updated=new UsernamePasswordAuthenticationToken(fresh,fresh.getPassword(),fresh.getAuthorities());
            updated.setDetails(context.getAuthentication().getDetails()); context.setAuthentication(updated);
            contexts.saveContext(context,request,response);
        }
        chain.doFilter(request,response);
    }
}
