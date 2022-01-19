package org.apache.apisix.plugin.runner.auth.iops;

import lombok.Data;

/**
 * @author wangweitian
 */
@Data
public class Permission {
    private Long id;
    private String pattern;
    private String method;
    private int allowlist;
}
