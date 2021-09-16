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
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.rdbms.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Impala extends DataStore {

   public String getTypeName() {
      return "rdbms_table";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();

          /* set guids */
          String dbGuid = String.valueOf(curGuid);
          String instanceGuid = String.valueOf(curGuid-1);

          /* get databasename and instance name from  capability property of service */
          String databasename = "";
          String instancename = "";
          Capability endpoint = safe(service.getCapabilities()).get("impala_endpoint");
          if (endpoint != null) {
             databasename = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("database"));
             instancename = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
          }
          if ((databasename == null) || databasename.trim().equals("")) {
             log.warn ("No database set for " + service.getName());
             context.log().error("No database set for " + service.getName());
          }

          if ((instancename == null) || instancename.trim().equals("")) {
             log.warn ("No artemis_instance_name set for " + service.getName());
             context.log().error("No artemis_instance_name set for " + service.getName());
          }

          if (vals.size() == 0) {
             log.warn ("No datasets found for " + service.getName());
             context.log().error("No datasets found for " + service.getName());
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
             tattribs.setQualifiedName(table + "@" + databasename + "@" + instancename);

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
           dbattribs.setQualifiedName(databasename + "@" + instancename);
           dbattribs.setRdbms_type("impala");

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
           iattribs.setName(instancename);
           iattribs.setQualifiedName(instancename);
           iattribs.setRdbms_type("impala");
           instance.setAttributes(iattribs);
           entities.put (instanceGuid, instance);

       } else {
          log.warn ("No datasets found for " + service.getName());
          context.log().error("No datasets found for " + service.getName());
       }

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("impala_endpoint") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("impala_endpoint") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
