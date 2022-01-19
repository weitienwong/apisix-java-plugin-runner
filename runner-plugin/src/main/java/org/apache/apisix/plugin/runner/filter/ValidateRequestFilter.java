package org.apache.apisix.plugin.runner.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.apisix.plugin.runner.HttpRequest;
import org.apache.apisix.plugin.runner.HttpResponse;
import org.apache.apisix.plugin.runner.auth.iops.RequestAuthenticationProvider;
import org.apache.apisix.plugin.runner.auth.iops.token.JwtProperties;
import org.apache.apisix.plugin.runner.auth.iops.token.TokenProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wangweitian
 */
@Slf4j
@Component
public class ValidateRequestFilter implements PluginFilter {
    private final RequestAuthenticationProvider provider;
    private final TokenProvider tokenProvider;
    private final JwtProperties prop;
    private final ObjectMapper mapper;
    private static final String TOKEN_KEY = "token-key";
    private static final String URL_PREFIX_KEY = "url-prefix";

    public ValidateRequestFilter(RequestAuthenticationProvider provider, TokenProvider tokenProvider, JwtProperties prop, ObjectMapper mapper) {
        log.info("ValidateRequestFilter 初始化............");
        this.provider = provider;
        this.tokenProvider = tokenProvider;
        this.prop = prop;
        this.mapper = mapper;
    }

    @Override
    public String name() {
       return "ValidateRequestFilter";
    }

    @Override
    public void filter(HttpRequest request, HttpResponse response, PluginFilterChain chain) {
        log.info("进入过滤器>>>>>>>>>");
        try {
            String path = request.getPath();
            String method = request.getMethod().name();
            log.info("Path : {}", path);
            log.info("Method : {}", method);

            String configStr = request.getConfig(this);

            Map<String, ?> conf = mapper.readValue(configStr, Map.class);
            Object tk = conf.get(TOKEN_KEY);

            String key = tk == null ? prop.getAuthorization() : String.valueOf(tk);
            String token = request.getHeader(key);
            //URL prefix
            String prefix = String.valueOf(conf.get(URL_PREFIX_KEY));
            if (StringUtils.hasText(prefix)) {
                path = path.replaceAll(prefix, "");
            }
            //the request is not in the white list
            if (!provider.allowlistMatching(path, method)) {
                log.info("uri[{} {}] was not in allow list", path, method);
                if (!StringUtils.hasText(token)) {
                   response401(request, response);
                }

                boolean b = tokenProvider.validate(token);
                // token expired
                if (!b) {
                   responseXxx(401, "Token was expired or invalid", request, response);
                }
                // extract user information from token
                HashMap<String, ?> obj = (HashMap)tokenProvider.extractAllClaims(token).get("user");

                List roles = (List) obj.get("roles");
                List<String> list = new ArrayList<>();
                roles.forEach(r -> list.add(r.toString()));
                // check permission
                boolean authenticate = provider.authenticate(path, method, list);
                if (!authenticate) {
                   response403(request, response);
                }
                // Adds user information to the request header
                request.setHeader("USER_INFO", mapper.writeValueAsString(obj));
            }
           chain.filter(request, response);
        } catch (Exception e) {
            log.error("校验失败：", e);
           response502(request, response);
        }
    }

    @Override
    public List<String> requiredVars() {
       return null;
    }

    @Override
    public Boolean requiredBody() {
       return Boolean.TRUE;
    }

    private void response401(HttpRequest request, HttpResponse response) {
        responseXxx(401, "Unauthorized", request, response);
    }

    private void response403(HttpRequest request, HttpResponse response) {
        responseXxx(403, "Access denied", request, response);
    }

    private void response502(HttpRequest request, HttpResponse response) {
        responseXxx(502, "Bad Gateway", request, response);
    }

    private void responseXxx(int code, String msg, HttpRequest request, HttpResponse response) {
        String json = request.getConfig(this);
        Map<String, Object> conf = new HashMap<>();
        try {
            conf = mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } finally {
            response.setStatusCode(code);
            String contentType = (String) conf.getOrDefault("Content-Type", "application/json");
            response.setHeader("Content-Type", contentType);
            String body = "{\n" +
                    "    \"code\": " + code + ",\n" +
                    "    \"msg\": \"" + msg + "\"\n" +
                    "}";
            response.setBody((String) conf.getOrDefault("body", body));
        }
    }
}
