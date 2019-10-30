package org.alien4cloud.plugin.datagouv_mls;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "datagouv_mls")
public class DatagouvMLSConfiguration {

    private String kafkaServers;
    private String topic;

    private String moduleDeleteCredentials;
    private String moduleDeleteUrl;

    private String applicationPostCredentials;
    private String applicationPostUrl;

    private String applicationDeleteCredentials;
    private String applicationDeleteAppliUrl;
    private String applicationDeleteModuleUrl;
}
