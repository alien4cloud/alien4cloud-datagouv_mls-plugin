package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.elasticsearch.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Elasticsearch extends DataStore {

   public String getTypeName() {
      return "elasticsearch_index";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* set guids */
       String clusterGuid = String.valueOf(curGuid);

       /* get protocol and instance name from  capability property of service */
       String protocol = "";
       String instance = "";
       Capability http = safe(service.getCapabilities()).get("http");
       if (http != null) {
          protocol = PropertyUtil.getScalarValue(safe(http.getProperties()).get("protocol"));
          instance = PropertyUtil.getScalarValue(safe(http.getProperties()).get("artemis_instance_name"));
       }

       /* process index */
       String sindex = "";
       sindex = PropertyUtil.getScalarValue(safe(service.getProperties()).get("index_basename"));

       if ((sindex == null) || sindex.trim().equals("")) {
          log.warn ("No index_basename set for " + service.getName());
          context.log().error("No index_basename set for " + service.getName());
       }

       if ((instance == null) || instance.trim().equals("")) {
          log.warn ("No artemis_instance_name set for " + service.getName());
          context.log().error("No artemis_instance_name set for " + service.getName());
       }

       Entity index = new Entity();
       entities.put (String.valueOf(startGuid), index);
       index.setTypeName(getTypeName());
       index.setGuid(String.valueOf(startGuid));
       startGuid--;

       IndexAttributes idxattribs = new IndexAttributes();
       idxattribs.setName(sindex);
       idxattribs.setQualifiedName(sindex + "@" + instance);
       idxattribs.setUri(protocol+"://" + instance + "/" + sindex);

       Entity icluster = new Entity();
       icluster.setGuid(clusterGuid);
       icluster.setTypeName("elasticsearch_cluster");
       icluster.setQualifiedName(instance);

       idxattribs.setCluster(icluster);
       index.setAttributes(idxattribs);

       /* process cluster */
       Entity cluster = new Entity();
       cluster.setGuid(clusterGuid);
       cluster.setTypeName("elasticsearch_cluster");
       Attributes cattribs = new Attributes();
       cattribs.setName(instance);
       cattribs.setQualifiedName(instance);
       cluster.setAttributes(cattribs);
       entities.put (clusterGuid, cluster);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("http") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("http") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
