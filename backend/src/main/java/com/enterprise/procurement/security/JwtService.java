package com.enterprise.procurement.security;

import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets; import javax.crypto.SecretKey; import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <p>两处刻意的"不宽容"：</p>
 * <ol>
 *   <li><b>不提供默认密钥</b>。原实现里 {@code @Value("${app.jwt.secret:change-this-...}")}
 *       带了个硬编码兜底值，意味着任何人 clone 下来直接跑，用的都是同一个公开密钥，
 *       等于任何人都能伪造合法 token。现在没配就启动失败，宁可起不来也不要静默不安全。</li>
 *   <li><b>密钥长度下限 32 字节</b>。HS256 要求 256 位密钥，短密钥在 jjwt 0.12 会直接拒绝，
 *       但旧代码用"拼接字符串"绕过了校验，这等于把安全等级偷偷降下来。现在显式报错。</li>
 * </ol>
 *
 * <p>token 里带 {@code tv}（token version）声明，与 {@code sys_user.token_version} 比对，
 * 用于实现"改密即失效"。</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 app.jwt.secret：请通过环境变量 JWT_SECRET 注入（至少 32 字节）。"
                            + "本项目不再提供内置默认密钥，因为默认密钥等于公开密钥。");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足 32 字节，HS256 要求 256 位密钥");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public String generate(String username, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 解析并验签；签名不对、过期、格式错都会抛 {@link JwtException}。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String username(String token) {
        return parse(token).getSubject();
    }

    public int tokenVersion(Claims claims) {
        Integer tv = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        return tv == null ? 0 : tv;
    }

    public boolean valid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
