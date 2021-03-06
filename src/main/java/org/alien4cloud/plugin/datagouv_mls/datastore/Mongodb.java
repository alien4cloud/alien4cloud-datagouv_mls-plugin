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
import org.alien4cloud.plugin.datagouv_mls.model.mongodb.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Mongodb extends DataStore {

   public String getTypeName() {
      return "mongo_collection";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String dbGuid = String.valueOf(curGuid);
          String clusterGuid = String.valueOf(curGuid-1);

          /* get databasename and instancename from  capability property of service */
          String databasename = "";
          String instance = "";
          Capability endpoint = safe(service.getCapabilities()).get("mongodb_endpoint");
          if (endpoint != null) {
             databasename = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("databasename"));
             instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
          }
          if ((databasename == null) || databasename.trim().equals("")) {
             log.warn ("No databasename set for " + service.getName());
             context.log().error("No databasename set for " + service.getName());
          }

          if ((instance == null) || instance.trim().equals("")) {
             log.warn ("No artemis_instance_name set for " + service.getName());
             context.log().error("No artemis_instance_name set for " + service.getName());
          }

          if (vals.size() == 0) {
             log.warn ("No datasets found for " + service.getName());
             context.log().error ("No datasets found for " + service.getName());
          }

          /* process collections */
          for (Object val : vals) {
             String collection = (String)val;

             Entity coll = new Entity();
             entities.put (String.valueOf(startGuid), coll);
             coll.setTypeName(getTypeName());
             coll.setGuid(String.valueOf(startGuid));
             startGuid--;

             CollectionAttributes collattribs = new CollectionAttributes();
             collattribs.setName(collection);
             collattribs.setQualifiedName(collection + "@" + databasename + "@" + instance);

             Entity db = new Entity();
             db.setGuid(dbGuid);
             db.setTypeName("mongo_db");
             db.setQualifiedName(databasename + "@" + instance);

             collattribs.setDb(db);
             coll.setAttributes(collattribs);
          }

           /* process db */
           Entity db = new Entity();
           db.setGuid(dbGuid);
           db.setTypeName("mongo_db");
           DbAttributes dbattribs = new DbAttributes();
           dbattribs.setName(databasename);
           dbattribs.setQualifiedName(databasename + "@" + instance);

           Entity icluster = new Entity();
           icluster.setGuid(clusterGuid);
           icluster.setTypeName("mongo_cluster");
           icluster.setQualifiedName(instance);

           dbattribs.setCluster(icluster);
           db.setAttributes(dbattribs);
           entities.put (dbGuid, db);

           /* process cluster */
           Entity cluster = new Entity();
           cluster.setGuid(clusterGuid);
           cluster.setTypeName("mongo_cluster");
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
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("mongodb_endpoint") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("mongodb_endpoint") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
