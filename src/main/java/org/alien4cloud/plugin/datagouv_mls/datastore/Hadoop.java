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
import org.alien4cloud.plugin.datagouv_mls.model.hadoop.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Hadoop extends DataStore {

   public String getTypeName() {
      return "hdfs_path";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* process path and instance name */
       String spath = "";
       String protocol = "";
       String instance = "";
       Capability endpoint = safe(service.getCapabilities()).get("hdfs_repository");
       if (endpoint != null) {
          spath = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("path"));
          protocol = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("protocol"));
          instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
       }

       if ((spath == null) || spath.trim().equals("")) {
          log.warn ("No path set for " + service.getName());
          context.log().error("No path set for " + service.getName());
       }

       if ((instance == null) || instance.trim().equals("")) {
          log.warn ("No artemis_instance_name set for " + service.getName());
          context.log().error("No artemis_instance_name set for " + service.getName());
       }

       Entity path = new Entity();
       entities.put (String.valueOf(startGuid), path);
       path.setTypeName(getTypeName());
       path.setGuid(String.valueOf(startGuid));
       startGuid--;

       PathAttributes attribs = new PathAttributes();
       attribs.setName(spath);
       attribs.setQualifiedName(protocol + "://" + instance + spath);
       attribs.setPath(spath);
       attribs.setClusterName(instance);
       path.setAttributes(attribs);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("hdfs_repository") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("hdfs_repository") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
