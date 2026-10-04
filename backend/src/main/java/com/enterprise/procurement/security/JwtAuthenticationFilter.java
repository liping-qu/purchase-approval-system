package com.enterprise.procurement.security;

import com.enterprise.procurement.persistence.mapper.CatalogMapper; import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException; import jakarta.servlet.http.*; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException; import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final CatalogMapper catalog;
    public JwtAuthenticationFilter(JwtService jwt,CatalogMapper catalog){this.jwt=jwt;this.catalog=catalog;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {String h=req.getHeader("Authorization"); if(h!=null&&h.startsWith("Bearer ")){String t=h.substring(7); if(jwt.valid(t)){String username=jwt.username(t); var u=catalog.user(username); if(u!=null){var authorities=catalog.roleCodes(u.getId()).stream().map(r->{String x=r.toUpperCase();return new SimpleGrantedAuthority(x.startsWith("ROLE_")?x:"ROLE_"+x);}).collect(Collectors.toSet()); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username,null,authorities));}}} chain.doFilter(req,res);}
}
