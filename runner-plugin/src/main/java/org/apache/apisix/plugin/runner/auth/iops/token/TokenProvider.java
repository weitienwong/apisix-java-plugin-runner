package org.apache.apisix.plugin.runner.auth.iops.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * @author wangweitian
 */
@Component
@Slf4j
public class TokenProvider {
    public final SecretKey key;

    public TokenProvider(JwtProperties prop) {
        this.key = new SecretKeySpec(prop.getSecret().getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
    }

    public boolean validate(String token) {
        boolean b = false;
        try {
            b = isNotExpired(token);
        } catch (Exception e) {
            log.error("解析TOKEN异常", e);
        }
        return b;
    }

    public boolean isNotExpired(String token) {
        return extractExpiration(token).after(new Date());
    }

    public JwtParserBuilder parser() {
        return Jwts.parserBuilder();
    }

    public Claims extractAllClaims(String token) {
        return parser().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public <T> T extractClaims(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public Date extractExpiration(String token) {
        return extractClaims(token, Claims::getExpiration);
    }

    public String extractSubject(String token) {
        return extractClaims(token, Claims::getSubject);
    }
}
