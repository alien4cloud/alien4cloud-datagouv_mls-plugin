package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.janusgraph.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Janusgraph extends DataStore {

   public String getTypeName() {
      return "janus_graph";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* set guids */
       String clusterGuid = String.valueOf(curGuid);

       String graph = "";
       String instance = "";
       Capability endpoint = safe(service.getCapabilities()).get("janusgraph");
       if (endpoint != null) {
          graph = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("graph"));
          instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
       }

       if ((graph == null) || graph.trim().equals("")) {
          log.warn ("No graph set for " + service.getName());
          context.log().error("No graph set for " + service.getName());
       }
       if ((instance == null) || instance.trim().equals("")) {
          log.warn ("No artemis_instance_name set for " + service.getName());
          context.log().error("No artemis_instance_name set for " + service.getName());
       }

       Entity egraph = new Entity();
       entities.put (String.valueOf(startGuid), egraph);
       egraph.setTypeName(getTypeName());
       egraph.setGuid(String.valueOf(startGuid));

       GraphAttributes gattribs = new GraphAttributes();
       gattribs.setName(graph);
       gattribs.setQualifiedName(graph + "@" + instance);

       Entity icluster = new Entity();
       icluster.setGuid(clusterGuid);
       icluster.setTypeName("janus_cluster");

       gattribs.setCluster(icluster);
       egraph.setAttributes(gattribs);

       /* process cluster */
       Entity cluster = new Entity();
       cluster.setGuid(clusterGuid);
       cluster.setTypeName("janus_cluster");
       Attributes cattribs = new Attributes();
       cattribs.setName(instance);
       cattribs.setQualifiedName(instance);
       cluster.setAttributes(cattribs);
       entities.put (clusterGuid, cluster);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("janusgraph") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("janusgraph") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
