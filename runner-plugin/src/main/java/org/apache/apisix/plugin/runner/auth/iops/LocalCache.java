package org.apache.apisix.plugin.runner.auth.iops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author wangweitian
 */
@Component
@Slf4j
public class LocalCache {
    private static final long RATE = 30000;

    private static final String KEY_PERMISSION_PREFIX = "permission:";
    private static final String KEY_PERMISSION_IDS = KEY_PERMISSION_PREFIX + "ids";
    private static final String KEY_ROLE_IDS = "role:ids";
    private static final String KEY_BINDING_PREFIX = "role:permissions:";
    static final Map<String, List<Permission>> BINDING = new HashMap<>();
    static final List<Permission> ALLOWLIST = new ArrayList<>();
    static final ReadWriteLock READ_WITE_LOCK = new ReentrantReadWriteLock();
    private final RedisTemplate template;
    private final StringRedisTemplate stringTemplate;
    private final ObjectMapper objectMapper;

    public LocalCache(RedisTemplate template, StringRedisTemplate stringTemplate, ObjectMapper objectMapper) {
        this.template = template;
        this.stringTemplate = stringTemplate;
        this.objectMapper = objectMapper;
    }

    @Async("task-pool")
    @Scheduled(fixedRate = RATE)
    public void initLocalCache() throws ExecutionException, InterruptedException {
        log.info("定时任务 初始化缓存");
        // load permissions
        CompletableFuture<Map<String, Permission>> f1 = CompletableFuture.supplyAsync(this::loadPermissions);
        // load relationship of role and permissions
        CompletableFuture<Map<String, List<String>>> f2 = CompletableFuture.supplyAsync(this::loadRelationship);
        // combine role with permissions
        CompletableFuture<Map<String, List<Permission>>> f3 = f1.thenCombine(f2, (permissions, relations) -> {
            Map<String, List<Permission>> map = new HashMap<>();
            relations.forEach((k, v) -> map.put(k, v.stream().map(permissions::get).collect(Collectors.toList())));
            return map;
        });
        READ_WITE_LOCK.writeLock().lock();
        BINDING.clear();
        BINDING.putAll(f3.get());
        Map<String, Permission> permissions = f1.get();
        ALLOWLIST.clear();
        permissions.forEach((k, v) -> {
            if (v.getAllowlist() == 1) {
                ALLOWLIST.add(v);
            }
        });
        READ_WITE_LOCK.writeLock().unlock();
        if (!CollectionUtils.isEmpty(ALLOWLIST)) {
            log.info("白名单");
            ALLOWLIST.forEach(p -> log.info("{} --> {}", p.getMethod(), p.getPattern()));
        } else {
            log.info("白名单为空");
        }
    }

    /**
     * load permissions form redis
     * @return permission id as key, permission as value
     */
    private Map<String, Permission> loadPermissions() {
        Map<String, Permission> permissionMap = new HashMap<>();
        SetOperations<String, String> ops = template.opsForSet();
        // get all id of permissions from redis
        Set<String> members = ops.members(KEY_PERMISSION_IDS);
        if (!CollectionUtils.isEmpty(members)) {
            //transform id to redis key
            List<String> keys = members.stream().map(id -> KEY_PERMISSION_PREFIX + id).collect(Collectors.toList());
            //load permission info
            List<String> values = stringTemplate.opsForValue().multiGet(keys);
            assert values != null;
            values.stream()
                    .map(this::convert)
                    .filter(Objects::nonNull)
                    .forEach(p -> {
                        permissionMap.put(this.mapKey(p), p);
                    });

        }
        return permissionMap;
    }

    /**
     * role and permissions binding map
     * @return binding map
     */
    private Map<String, List<String>> loadRelationship() {
        SetOperations<String, String> ops = template.opsForSet();
        // role id collection
        Set<String> members = ops.members(KEY_ROLE_IDS);
        // role and permissions collection map
        Map<String, List<String>> relations = new HashMap<>();
        assert members != null;
        members.forEach(id -> {
            Set<String> set = ops.members(KEY_BINDING_PREFIX + id);
            if (!CollectionUtils.isEmpty(set)) {
                relations.put(id, new ArrayList<>(set));
            }
        });
        return relations;
    }

    /**
     * Convert a json string to {@link Permission}
     * @param json  a json string
     * @return {@link Permission}
     */
    private Permission convert(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, Permission.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String mapKey(Permission permission) {
        return  permission.getId().toString();
    }

}
