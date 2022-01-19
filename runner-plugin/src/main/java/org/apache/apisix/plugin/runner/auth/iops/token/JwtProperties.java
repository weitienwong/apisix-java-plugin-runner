package org.apache.apisix.plugin.runner.auth.iops.token;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author wangweitian
 */
@ConfigurationProperties(prefix = "jwt")
@Component
@Data
public class JwtProperties {
    /**
     * 秘钥
     */
    public String secret = "Bizseer_AIOPSBizseer_AIOPSBizseer_AIOPS";
    /**
     * 签发者
     */
    public String issuer = "Bizseer";
    /**
     * 过期时间(秒)
     */
    public int expiration = 86400;
    /**
     * Header中获取token的属性
     */
    public String authorization = "Authorization";

}
