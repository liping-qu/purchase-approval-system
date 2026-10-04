package com.enterprise.procurement.security;

import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import java.nio.charset.StandardCharsets; import javax.crypto.SecretKey; import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationMs;
    public JwtService(@Value("${app.jwt.secret:change-this-secret-key-for-production-please}") String secret,@Value("${app.jwt.expiration-ms:86400000}") long expirationMs){String s=secret.length()<32?secret+"-change-this-secret-key":secret; key=Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8)); this.expirationMs=expirationMs;}
    public String generate(String username){Date now=new Date(); return Jwts.builder().subject(username).issuedAt(now).expiration(new Date(now.getTime()+expirationMs)).signWith(key).compact();}
    public String username(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
    public boolean valid(String token){try{username(token); return true;}catch(JwtException|IllegalArgumentException e){return false;}}
}
