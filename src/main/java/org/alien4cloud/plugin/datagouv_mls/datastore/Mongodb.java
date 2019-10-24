package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.mongodb.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mongodb extends DataStore {

   public String getTypeName() {
      return "mongo_collection";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String dbGuid = String.valueOf(curGuid);
          String clusterGuid = String.valueOf(curGuid-1);

          /* get ip address from service attribute */
          String ipAddress = "localhost";
          if (service instanceof ServiceNodeTemplate) {
             ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
             ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.mongodb_endpoint.ip_address");
          }

          /* get databasename from  capability property of service */
          String databasename = "";
          Capability endpoint = safe(service.getCapabilities()).get("mongodb_endpoint");
          if (endpoint != null) {
             databasename = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("databasename"));
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
             collattribs.setQualifiedName(collection + "." + databasename + "." + ipAddress);

             Entity db = new Entity();
             db.setGuid(dbGuid);
             db.setTypeName("mongo_db");

             collattribs.setDb(db);
             coll.setAttributes(collattribs);
          }

           /* process db */
           Entity db = new Entity();
           db.setGuid(dbGuid);
           db.setTypeName("mongo_db");
           DbAttributes dbattribs = new DbAttributes();
           dbattribs.setName(databasename);
           dbattribs.setQualifiedName(databasename + "." + ipAddress);

           Entity icluster = new Entity();
           icluster.setGuid(clusterGuid);
           icluster.setTypeName("mongo_cluster");

           dbattribs.setCluster(icluster);
           db.setAttributes(dbattribs);
           entities.put (dbGuid, db);

           /* process cluster */
           Entity cluster = new Entity();
           cluster.setGuid(clusterGuid);
           cluster.setTypeName("mongo_cluster");
           Attributes cattribs = new Attributes();
           cattribs.setName(ipAddress);
           cattribs.setQualifiedName(ipAddress);
           cluster.setAttributes(cattribs);
           entities.put (clusterGuid, cluster);

       }

       return entities;
    }

}
