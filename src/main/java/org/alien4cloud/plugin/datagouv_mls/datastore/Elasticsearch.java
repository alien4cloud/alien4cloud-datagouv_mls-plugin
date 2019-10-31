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
import org.alien4cloud.plugin.datagouv_mls.model.elasticsearch.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Elasticsearch extends DataStore {

   public String getTypeName() {
      return "elasticsearch_index";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* set guids */
       String clusterGuid = String.valueOf(curGuid);

       /* get ip address from service attribute */
       String ipAddress = "localhost";
       if (service instanceof ServiceNodeTemplate) {
          ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
          ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.http.ip_address");
       }

       /* process index */
       String sindex = "";
       sindex = PropertyUtil.getScalarValue(safe(service.getProperties()).get("index_basename"));
       Entity index = new Entity();
       entities.put (String.valueOf(startGuid), index);
       index.setTypeName(getTypeName());
       index.setGuid(String.valueOf(startGuid));
       startGuid--;

       IndexAttributes idxattribs = new IndexAttributes();
       idxattribs.setName(sindex);
       idxattribs.setQualifiedName(sindex + "." + ipAddress);

       Entity icluster = new Entity();
       icluster.setGuid(clusterGuid);
       icluster.setTypeName("elasticsearch_cluster");
       icluster.setQualifiedName(ipAddress);

       idxattribs.setCluster(icluster);
       index.setAttributes(idxattribs);

       /* process cluster */
       Entity cluster = new Entity();
       cluster.setGuid(clusterGuid);
       cluster.setTypeName("elasticsearch_cluster");
       Attributes cattribs = new Attributes();
       cattribs.setName(ipAddress);
       cattribs.setQualifiedName(ipAddress);
       cluster.setAttributes(cattribs);
       entities.put (clusterGuid, cluster);

       return entities;
    }

}
