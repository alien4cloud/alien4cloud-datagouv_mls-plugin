package org.alien4cloud.plugin.datagouv_mls;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "datagouv_mls")
public class DatagouvMLSConfiguration {

    private String kafkaServers;
    private String topic;

    private Map<String,String> producerProperties = new HashMap<String,String>();

    private String moduleDeleteCredentials;
    private String moduleDeleteUrl;

    private String applicationPostCredentials;
    private String applicationPostUrl;

    private String applicationDeleteCredentials;
    private String applicationDeleteAppliUrl;
    private String applicationDeleteModuleUrl;

    private String getPdsCredentials;
    private String getPdsUrl;

    private String pdsIhmUrl;

    private String pvStorageClass;

    private String pvNodeType = "artemis.pvk8s.pub.nodes.PVK8S";
    private String pvVersion = "3.0-SNAPSHOT";
}
