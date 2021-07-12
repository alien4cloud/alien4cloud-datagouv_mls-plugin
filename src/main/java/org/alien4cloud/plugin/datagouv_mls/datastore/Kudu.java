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
import org.alien4cloud.plugin.datagouv_mls.model.kudu.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Kudu extends DataStore {

   public String getTypeName() {
      return "kudu_table";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String clusterGuid = String.valueOf(curGuid);

          /* get instance name from  capability property of service */
          String instance = "";
          Capability endpoint = safe(service.getCapabilities()).get("kudu_endpoint");
          if (endpoint != null) {
             instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
          }

          if ((instance == null) || instance.trim().equals("")) {
             log.warn ("No artemis_instance_name set for " + service.getName());
             context.log().error("No artemis_instance_name set for " + service.getName());
          }

          if (vals.size() == 0) {
             log.warn ("No datasets found for " + service.getName());
             context.log().error ("No datasets found for " + service.getName());
          }

          /* process tables */
          for (Object val : vals) {
             String table = (String)val;

             Entity ktable = new Entity();
             entities.put (String.valueOf(startGuid), ktable);
             ktable.setTypeName(getTypeName());
             ktable.setGuid(String.valueOf(startGuid));
             startGuid--;

             TableAttributes tattribs = new TableAttributes();
             tattribs.setName(table);
             tattribs.setQualifiedName(table + "@" + instance);

             Entity cluster = new Entity();
             cluster.setGuid(clusterGuid);
             cluster.setTypeName("kudu_cluster");

             tattribs.setCluster(cluster);
             ktable.setAttributes(tattribs);
          }
           /* process cluster */
           Entity cluster = new Entity();
           cluster.setGuid(clusterGuid);
           cluster.setTypeName("kudu_cluster");
           Attributes cattribs = new Attributes();
           cattribs.setName(instance);
           cattribs.setQualifiedName(instance);
           cluster.setAttributes(cattribs);
           entities.put (clusterGuid, cluster);

       } else {
          log.warn ("No datasets found for " + service.getName());
          context.log().error ("No datasets found for " + service.getName());
       }

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("kudu_endpoint") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("kudu_endpoint") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
