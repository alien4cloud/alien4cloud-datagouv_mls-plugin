package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import static alien4cloud.utils.AlienUtils.safe;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.datastore.*;
import org.alien4cloud.plugin.datagouv_mls.model.*;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component("datagouv_mls-modifier")
public class DatagouvMLSModifier extends TopologyModifierSupport {

    private int guid = -1;

    private String getGuid() {
       String ret = String.valueOf(guid);
       guid--;
       return ret;
    }

    private static Map<String, Class> dataStoreTypes = Stream.of(new Object[][] { 
        { "artemis.redis.pub.capabilities.Redis", Redis.class }, 
        { "artemis.mongodb.pub.capabilities.MongoDb", Mongodb.class }, 
        { "artemis.mariadb.pub.capabilities.Mariadb", Mariadb.class }, 
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Class) data[1]));

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            context.getLog().error("Couldn't process Datagouv-MLS modifier");
            log.warn ("Couldn't process Datagouv-MLS modifier, got " + e.getMessage());
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {
       
       String now = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(new Date()).toString();
       guid = -1;
       String appliId = getGuid();

       /* application : list of entities & referredEntities */
       Application fullAppli = new Application();
       List<Entity> entities = new ArrayList<Entity>();
       Map<String,Entity> referredEntities = new HashMap<String,Entity>();
       fullAppli.setEntities(entities);
       fullAppli.setReferredEntities(referredEntities);

       /* first entity describes application */
       Entity appli = new Entity();
       appli.setTypeName(DatagouvMLSConstants.APPLI_NAME);
       Attributes attribs = new Attributes();
       attribs.setName(context.getEnvironmentContext().get().getApplication().getName());
       String appliName = context.getEnvironmentContext().get().getApplication().getName() + "-" +
                          context.getEnvironmentContext().get().getEnvironment().getName();
       attribs.setQualifiedName(appliName);
       attribs.setVersion(context.getEnvironmentContext().get().getEnvironment().getVersion());
       attribs.setStartTime(now);
       appli.setAttributes(attribs);
       appli.setGuid(appliId);

       entities.add(appli);

       /* process nodes */
       Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
       for (String nodeName : nodeTemplates.keySet()) {
          /* nodes to be added contain a "container" property */
          if ( safe(nodeTemplates.get(nodeName).getProperties()).get("container") != null) {
             NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplates.get(nodeName).getType());
             String version = nodeType.getArchiveVersion();
             log.info ("Processing node " + nodeName + " - " + nodeTemplates.get(nodeName).getType() + " :" + version);

             String moduleGuid = getGuid();

             /* module entity descriptor */
             Entity module = new Entity();
             module.setTypeName(DatagouvMLSConstants.MODULE_INSTANCE_NAME);
             attribs = new Attributes();
             attribs.setName(nodeName);
             attribs.setQualifiedName(nodeName + "-" + appliName);
             attribs.setStartTime(now);
             Entity instance = new Entity();
             instance.setTypeName(DatagouvMLSConstants.MODULE_NAME);
             instance.setGuid(moduleGuid);
             attribs.setInstanceOf(instance);
             Entity member = new Entity();
             member.setTypeName(DatagouvMLSConstants.APPLI_NAME);
             member.setGuid(appliId);
             attribs.setMemberOf(member);

             /* parse relations to generate inputs & outputs (datastores) and datastores descriptors */
             Map<String, RelationshipTemplate> relationships = nodeTemplates.get(nodeName).getRelationships();
             List<Entity> inputs = new ArrayList<Entity>();
             List<Entity> outputs = new ArrayList<Entity>();
             for (String nrel : safe(relationships).keySet()) {
                 /* datastores relations are in dataStoreTypes map */
                 if (dataStoreTypes.keySet().contains(relationships.get(nrel).getRequirementType())) {

                    log.info ("Processing relation " + relationships.get(nrel).getRequirementType() + " with target " + relationships.get(nrel).getTarget());

                    /* get datastore service node */
                    NodeTemplate serviceNode = nodeTemplates.get(relationships.get(nrel).getTarget());

                    try {
                       DataStore ds = (DataStore)dataStoreTypes.get(relationships.get(nrel).getRequirementType()).newInstance();
                       String typeName = ds.getTypeName();
                       /* get number of first level elements for datastore in order to generate inputs & outputs */
                       int nb = ds.getNbSets(relationships.get(nrel).getProperties());
                       int startGuid = guid;

                       /* generate inputs & outputs according to number of first level elements returned by datastore */
                       for (int cur = 0; cur < nb ; cur ++) {
                          Entity xput = new Entity();
                          xput.setTypeName(typeName);
                          xput.setGuid(getGuid());
                          inputs.add(xput);
                          outputs.add(xput);
                       }

                       /* generate reference entities for this datastore */
                       Map<String, Entity> dsEntities = ds.getEntities(relationships.get(nrel).getProperties(), serviceNode, startGuid, guid);
                       guid -= (dsEntities.size() - nb);

                       referredEntities.putAll(dsEntities);
                    } catch (Exception e) {
                       log.error ("Got exception : " + e.getMessage());
                    }
                 }
             }
             if (!inputs.isEmpty()) {
                attribs.setInputs(inputs);
                attribs.setOutputs(outputs);
             }

             module.setAttributes(attribs);
             entities.add(module);

             /* module reference entity */
             Entity refModule = new Entity();
             refModule.setGuid(moduleGuid);
             refModule.setTypeName(DatagouvMLSConstants.MODULE_NAME);
             attribs = new Attributes();
             String qname = nodeTemplates.get(nodeName).getType();
             attribs.setName(qname.substring(qname.lastIndexOf(".")+1));
             attribs.setQualifiedName(qname);
             attribs.setVersion(version);
             refModule.setAttributes(attribs);

             referredEntities.put(moduleGuid, refModule);
          }
       }

       try {
          log.info ("TOPO=" + (new ObjectMapper()).writeValueAsString(topology));
          String json = (new ObjectMapper()).writeValueAsString(fullAppli);
          log.info ("JSON=" + json);
       } catch (Exception e) {
          log.error ("Got exception:" + e.getMessage());
       }
    }
}
