# Alien4cloud DataGouv-MLS plugin

Plugins that publishes information on modules imports/deletions and applications deployments/undeployments to DataGouv-MLS

## Configuration
In alien4cloud main configuration file, set:

- datagouv_mls.kafkaServers : Kafka server (module import)
- datagouv_mls.topic : Kafka topic (module import)
- datagouv_mls.moduleDeleteCredentials : credentials user:password (module deletion)
- datagouv_mls.moduleDeleteUrl : REST URL (module deletion)
