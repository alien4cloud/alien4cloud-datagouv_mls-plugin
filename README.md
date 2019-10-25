# Alien4cloud DataGouv-MLS plugin

Plugins that publishes information on modules imports/deletions and applications deployments/undeployments to DataGouv-MLS
datagouv_mls-pre_match-modifier must be set with pre-node-match phase
datagouv_mls-modifier must be set with post-matched-node-setup phase and before kubernetes plugin

## Configuration
In alien4cloud main configuration file, set:

- datagouv_mls.kafkaServers : Kafka server (module import)
- datagouv_mls.topic : Kafka topic (module import)
- datagouv_mls.moduleDeleteCredentials : credentials user:password (module deletion)
- datagouv_mls.moduleDeleteUrl : REST URL (module deletion)
- datagouv_mls.applicationDeployCredentials : credentials user:password (application deployment)
- datagouv_mls.applicationDeployUrl : REST URL (application deployment)
