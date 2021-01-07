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
import org.alien4cloud.plugin.datagouv_mls.model.cassandra.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Cassandra extends DataStore {

   public String getTypeName() {
      return "cassandra_table";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String ksGuid = String.valueOf(curGuid);
          String clusterGuid = String.valueOf(curGuid-1);

          /* get keyspace and instance name from  capability property of service */
          String ks = "";
          String instance = "";
          Capability endpoint = safe(service.getCapabilities()).get("cassandra_endpoint");
          if (endpoint != null) {
             ks = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("keyspace"));
             instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
          }
          if ((ks == null) || ks.trim().equals("")) {
             log.warn ("No keyspace set for " + service.getName());
             context.log().error("No keyspace set for " + service.getName());
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
             String ntable = (String)val;

             Entity table = new Entity();
             entities.put (String.valueOf(startGuid), table);
             table.setTypeName(getTypeName());
             table.setGuid(String.valueOf(startGuid));
             startGuid--;

             TableAttributes tattribs = new TableAttributes();
             tattribs.setName(ntable);
             tattribs.setQualifiedName(ntable + "@" + ks + "@" + instance);

             Entity keyspace = new Entity();
             keyspace.setGuid(ksGuid);
             keyspace.setTypeName("cassandra_keyspace");
             keyspace.setQualifiedName(ks + "@" + instance);

             tattribs.setKeyspace(keyspace);
             table.setAttributes(tattribs);
          }

           /* process keyspace */
           Entity keyspace = new Entity();
           keyspace.setGuid(ksGuid);
           keyspace.setTypeName("cassandra_keyspace");
           KeyspaceAttributes kattribs = new KeyspaceAttributes();
           kattribs.setName(ks);
           kattribs.setQualifiedName(ks + "@" + instance);

           Entity icluster = new Entity();
           icluster.setGuid(clusterGuid);
           icluster.setTypeName("cassandra_cluster");
           icluster.setQualifiedName(instance);

           kattribs.setCluster(icluster);
           keyspace.setAttributes(kattribs);
           entities.put (ksGuid, keyspace);

           /* process cluster */
           Entity cluster = new Entity();
           cluster.setGuid(clusterGuid);
           cluster.setTypeName("cassandra_cluster");
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
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("cassandra_endpoint") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("cassandra_endpoint") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
