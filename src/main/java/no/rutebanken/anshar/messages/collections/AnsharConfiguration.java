package no.rutebanken.anshar.messages.collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnsharConfiguration {

    @Value("${rutebanken.kubernetes.url:}")
    private String kubernetesUrl;

    @Value("${rutebanken.kubernetes.enabled:true}")
    private boolean kubernetesEnabled;

    @Value("${rutebanken.kubernetes.namespace:default}")
    private String namespace;


    @Value("${rutebanken.hazelcast.management.url:}")
    private String hazelcastManagementUrl;

    public String getHazelcastManagementUrl() {
        return hazelcastManagementUrl;
    }

    public String getKubernetesUrl() {
        return kubernetesUrl;
    }

    public boolean isKubernetesEnabled() {
        return kubernetesEnabled;
    }

    public String getNamespace() {
        return namespace;
    }
}
