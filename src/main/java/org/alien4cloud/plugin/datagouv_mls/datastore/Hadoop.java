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
import org.alien4cloud.plugin.datagouv_mls.model.hadoop.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hadoop extends DataStore {

   public String getTypeName() {
      return "hdfs_path";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       String ipAddress = "";
       if (service instanceof ServiceNodeTemplate) {
          ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
          ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.hdfs_repository.ip_address");
       }

       /* process path */
       String spath = "";
       String protocol = "";
       Capability endpoint = safe(service.getCapabilities()).get("hdfs_repository");
       if (endpoint != null) {
          spath = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("path"));
          protocol = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("protocol"));
       }
       Entity path = new Entity();
       entities.put (String.valueOf(startGuid), path);
       path.setTypeName(getTypeName());
       path.setGuid(String.valueOf(startGuid));
       startGuid--;

       PathAttributes attribs = new PathAttributes();
       attribs.setName(spath);
       attribs.setQualifiedName(protocol + "://" + ipAddress + spath);
       attribs.setPath(spath);
       attribs.setClusterName(ipAddress);
       path.setAttributes(attribs);

       return entities;
    }

    public void setCredentials (NodeTemplate service, String user, String password) {
        Capability endpoint = safe(service.getCapabilities()).get("hdfs_repository");
        if (endpoint != null) {
           endpoint.getProperties().put("username", new ScalarPropertyValue(user));
           endpoint.getProperties().put("password", new ScalarPropertyValue(password));
        }
    }

}
