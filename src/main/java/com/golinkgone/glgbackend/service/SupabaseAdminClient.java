package com.golinkgone.glgbackend.service;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseAdminClient {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private final RestClient restClient;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.project-url}")
    private String projectUrl;

    public SupabaseAdminClient() {
        this.restClient = RestClient.create();
    }

    public void deleteUser(String userId) {

        if (userId == null || !UUID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException("Invalid userId format");
        }

        restClient
                .delete()
                .uri(projectUrl + "/auth/v1/admin/users/" + userId)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .toBodilessEntity();
    }
}
