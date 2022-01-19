package org.apache.apisix.plugin.runner.auth.iops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.pattern.PathPatternParser;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wangweitian
 */
@Slf4j
@Component
public class DefaultRequestAuthenticationProvider implements RequestAuthenticationProvider {
    @Override
    public boolean authenticate(String path, String method, List<String> roles) {
        List<Permission> permissions = new ArrayList<>();
        if (!CollectionUtils.isEmpty(roles)) {
            LocalCache.READ_WITE_LOCK.readLock().lock();
            roles.forEach(r -> {
                permissions.addAll(LocalCache.BINDING.get(r));
            });
            LocalCache.READ_WITE_LOCK.readLock().unlock();
        }
        if (!CollectionUtils.isEmpty(permissions)) {
            return permissions.stream().anyMatch(p -> matches(p, method, path));
        }
        return false;
    }

    @Override
    public boolean allowlistMatching(String path, String method) {
        LocalCache.READ_WITE_LOCK.readLock().lock();
        for (Permission p : LocalCache.ALLOWLIST) {
            if (matches(p, method, path)) {
                log.info("Path: {}, Method: {} 匹配权限: {}", path, method, p);
                return true;
            }
        }
        LocalCache.READ_WITE_LOCK.readLock().unlock();
        return false;
    }

    private boolean matches(Permission p, String method, String path) {
        if (p == null) {
            return false;
        }
        return method.equalsIgnoreCase(p.getMethod()) &&
                PathPatternParser.defaultInstance.parse(p.getPattern()).matches(PathContainer.parsePath(path));
    }
}
