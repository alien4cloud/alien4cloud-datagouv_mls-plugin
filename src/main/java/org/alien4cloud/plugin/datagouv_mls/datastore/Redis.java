package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.redis.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Redis extends DataStore {

   public String getTypeName() {
      return "redis_key";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String clusterGuid = String.valueOf(curGuid);

          /* get ip address from service attribute */
          String ipAddress = "localhost";
          if (service instanceof ServiceNodeTemplate) {
             ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
             ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.redis_endpoint.ip_address");
          }

          if (vals.size() == 0) {
             log.warn ("No datasets found for " + service.getName());
          }

          /* process keys */
          for (Object val : vals) {
             String skey = (String)val;

             Entity key = new Entity();
             entities.put (String.valueOf(startGuid), key);
             key.setTypeName(getTypeName());
             key.setGuid(String.valueOf(startGuid));
             startGuid--;

             KeyAttributes keyattribs = new KeyAttributes();
             keyattribs.setName(skey);
             keyattribs.setQualifiedName(skey + "." + ipAddress);

             Entity cluster = new Entity();
             cluster.setGuid(clusterGuid);
             cluster.setTypeName("redis_cluster");
             cluster.setQualifiedName(ipAddress);

             keyattribs.setCluster(cluster);
             key.setAttributes(keyattribs);
          }

           /* process cluster */
           Entity cluster = new Entity();
           cluster.setGuid(clusterGuid);
           cluster.setTypeName("redis_cluster");
           Attributes cattribs = new Attributes();
           cattribs.setName(ipAddress);
           cattribs.setQualifiedName(ipAddress);
           cluster.setAttributes(cattribs);
           entities.put (clusterGuid, cluster);

       } else {
          log.warn ("No datasets found for " + service.getName());
       }

       return entities;
    }

    public void setCredentials (NodeTemplate service, String user, String password) {
        Capability endpoint = safe(service.getCapabilities()).get("redis_endpoint");
        if (endpoint != null) {
           endpoint.getProperties().put("user", new ScalarPropertyValue(user));
           endpoint.getProperties().put("password", new ScalarPropertyValue(password));
        }
    }

}
