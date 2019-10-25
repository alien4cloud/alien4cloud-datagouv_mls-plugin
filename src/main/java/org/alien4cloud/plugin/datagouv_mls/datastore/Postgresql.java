package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.mariadb.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Postgresql extends DataStore {

   public String getTypeName() {
      return "rdbms_table";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String dbGuid = String.valueOf(curGuid);
          String instanceGuid = String.valueOf(curGuid-1);

          /* get ip address from service attribute */
          String ipAddress = "localhost";
          if (service instanceof ServiceNodeTemplate) {
             ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
             ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.postgresql_endpoint.ip_address");
          }

          /* get databasename from  capability property of service */
          String databasename = "";
          Capability endpoint = safe(service.getCapabilities()).get("postgresql_endpoint");
          if (endpoint != null) {
             databasename = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("database"));
          }

          /* process tables */
          for (Object val : vals) {
             String table = (String)val;

             Entity dbtable = new Entity();
             entities.put (String.valueOf(startGuid), dbtable);
             dbtable.setTypeName(getTypeName());
             dbtable.setGuid(String.valueOf(startGuid));
             startGuid--;

             TableAttributes tattribs = new TableAttributes();
             tattribs.setName(table);
             tattribs.setQualifiedName(table + "." + databasename + "@" + ipAddress);

             Entity db = new Entity();
             db.setGuid(dbGuid);
             db.setTypeName("rdbms_db");

             tattribs.setDb(db);
             dbtable.setAttributes(tattribs);
          }

           /* process db */
           Entity db = new Entity();
           db.setGuid(dbGuid);
           db.setTypeName("rdbms_db");
           DbAttributes dbattribs = new DbAttributes();
           dbattribs.setName(databasename);
           dbattribs.setQualifiedName(databasename + "@" + ipAddress);

           Entity iinstance = new Entity();
           iinstance.setGuid(instanceGuid);
           iinstance.setTypeName("rdbms_instance");

           dbattribs.setInstance(iinstance);
           db.setAttributes(dbattribs);
           entities.put (dbGuid, db);

           /* process instance */
           Entity instance = new Entity();
           instance.setGuid(instanceGuid);
           instance.setTypeName("rdbms_instance");
           InstAttributes iattribs = new InstAttributes();
           iattribs.setName(ipAddress);
           iattribs.setQualifiedName(ipAddress);
           iattribs.setRdbms_type("postgresql");
           instance.setAttributes(iattribs);
           entities.put (instanceGuid, instance);

       }

       return entities;
    }

    public void setCredentials (NodeTemplate service, String user, String password) {
        Capability endpoint = safe(service.getCapabilities()).get("postgresql_endpoint");
        if (endpoint != null) {
           endpoint.getProperties().put("user", new ScalarPropertyValue(user));
           endpoint.getProperties().put("password", new ScalarPropertyValue(password));
        }
    }

}
