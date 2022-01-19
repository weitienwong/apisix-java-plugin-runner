package org.apache.apisix.plugin.runner.auth.iops;

import java.util.List;

public interface RequestAuthenticationProvider {
    boolean authenticate(String path, String method, List<String> roles);

    boolean allowlistMatching(String path, String method);
}
