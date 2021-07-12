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
import org.alien4cloud.plugin.datagouv_mls.model.pv.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PV extends DataStore {

   public String getTypeName() {
      return "k8s_pv";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* process path and instance name */
       String qname = "";
       String shortname = "";
       String accessModes = "";
       String storageClass = "";
       Capability endpoint = safe(service.getCapabilities()).get("pvk8s_endpoint");
       if (endpoint != null) {
          qname = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("qnamePV"));
          shortname = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("shortname"));
          accessModes = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("accessModes"));
          storageClass = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("storageClass"));
       }

       if ((qname == null) || qname.trim().equals("")) {
          log.warn ("No qnamePV set for " + service.getName());
          context.log().error("No qnamePV set for " + service.getName());
       }

       if ((shortname == null) || shortname.trim().equals("")) {
          log.warn ("No shortname set for " + service.getName());
          context.log().error("No shortname set for " + service.getName());
       }

       if ((accessModes == null) || accessModes.trim().equals("")) {
          log.warn ("No accessModes set for " + service.getName());
          context.log().error("No accessModes set for " + service.getName());
       }

       if ((storageClass == null) || storageClass.trim().equals("")) {
          log.warn ("No storageClass set for " + service.getName());
          context.log().error("No storageClass set for " + service.getName());
       }
       Entity pv = new Entity();
       entities.put (String.valueOf(startGuid), pv);
       pv.setTypeName(getTypeName());
       pv.setGuid(String.valueOf(startGuid));
       startGuid--;

       PVAttributes attribs = new PVAttributes();
       attribs.setName(shortname);
       attribs.setQualifiedName(qname);
       attribs.setAccessModes(accessModes);
       attribs.setStorageClass(storageClass);
       pv.setAttributes(attribs);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("pvk8s_endpoint") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("pvk8s_endpoint") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
