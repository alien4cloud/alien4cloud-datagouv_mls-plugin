package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
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

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String ksGuid = String.valueOf(curGuid);
          String clusterGuid = String.valueOf(curGuid-1);

          /* get ip address from service attribute */
          String ipAddress = "localhost";
          if (service instanceof ServiceNodeTemplate) {
             ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
             ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.cassandra_endpoint.ip_address");
          }

          /* get keyspace from  capability property of service */
          String ks = "";
          Capability endpoint = safe(service.getCapabilities()).get("cassandra_endpoint");
          if (endpoint != null) {
             ks = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("keyspace"));
          }

          if (vals.size() == 0) {
             log.warn ("No datasets found for " + service.getName());
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
             tattribs.setQualifiedName(ntable + "." + ks + "." + ipAddress);

             Entity keyspace = new Entity();
             keyspace.setGuid(ksGuid);
             keyspace.setTypeName("cassandra_keyspace");
             keyspace.setQualifiedName(ks + "." + ipAddress);

             tattribs.setKeyspace(keyspace);
             table.setAttributes(tattribs);
          }

           /* process keyspace */
           Entity keyspace = new Entity();
           keyspace.setGuid(ksGuid);
           keyspace.setTypeName("cassandra_keyspace");
           KeyspaceAttributes kattribs = new KeyspaceAttributes();
           kattribs.setName(ks);
           kattribs.setQualifiedName(ks + "." + ipAddress);

           Entity icluster = new Entity();
           icluster.setGuid(clusterGuid);
           icluster.setTypeName("cassandra_cluster");
           icluster.setQualifiedName(ipAddress);

           kattribs.setCluster(icluster);
           keyspace.setAttributes(kattribs);
           entities.put (ksGuid, keyspace);

           /* process cluster */
           Entity cluster = new Entity();
           cluster.setGuid(clusterGuid);
           cluster.setTypeName("cassandra_cluster");
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
