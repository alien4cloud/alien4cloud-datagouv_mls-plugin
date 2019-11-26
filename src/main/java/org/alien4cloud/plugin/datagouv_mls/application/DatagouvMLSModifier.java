package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;

import org.alien4cloud.alm.deployment.configuration.flow.EnvironmentContext;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.utils.ProcessLauncher;
import org.alien4cloud.plugin.datagouv_mls.utils.TopologyUtils;
import org.alien4cloud.plugin.datagouv_mls.datastore.DataStore;
import org.alien4cloud.plugin.datagouv_mls.model.*;
import static org.alien4cloud.plugin.kubernetes.csar.Version.K8S_CSAR_VERSION;
import static org.alien4cloud.plugin.kubernetes.modifier.KubernetesAdapterModifier.K8S_TYPES_KUBE_NAMESPACE;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Resource;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("datagouv_mls-modifier")
public class DatagouvMLSModifier extends TopologyModifierSupport {

    @Resource
    private MetaPropertiesService metaPropertiesService;

    @Resource
    private DatagouvMLSConfiguration configuration;

    private int guid = -1;

    private String getGuid() {
       String ret = String.valueOf(guid);
       guid--;
       return ret;
    }

    private static final String CUNAME_PROP = "Cas d'usage";

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            log.warn ("Couldn't process Datagouv-MLS modifier, got " + e.getMessage());
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {
       
       String now = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(new Date()).toString();
       guid = -1;
       String appliId = getGuid();
       String appVersion = context.getEnvironmentContext().get().getEnvironment().getVersion();

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
       attribs.setVersion(appVersion);
       attribs.setStartTime(now);
       attribs.setStatus("PROPOSED");
       appli.setAttributes(attribs);
       appli.setGuid(appliId);

       entities.add(appli);

       /* maps to store links between modules and services nodes, and services and datastores objects */
       Map<String, List<NodeTemplate>> nodesToServices = new HashMap<String, List<NodeTemplate>>();
       Map<String, DataStore> servicesToDs = new HashMap<String, DataStore>();

       /* process nodes */
       Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
       for (String nodeName : nodeTemplates.keySet()) {
          /* nodes to be added contain a "container" property */
          if ( safe(nodeTemplates.get(nodeName).getProperties()).get("container") != null) {
             NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplates.get(nodeName).getType());
             String version = nodeType.getArchiveVersion();
             log.info ("Processing node " + nodeName);

             List<NodeTemplate> serviceNodes = new ArrayList<NodeTemplate>();
             nodesToServices.put (nodeName, serviceNodes);

             String moduleGuid = getGuid();

             /* module entity descriptor */
             Entity module = new Entity();
             module.setTypeName(DatagouvMLSConstants.MODULE_INSTANCE_NAME);
             attribs = new Attributes();
             attribs.setName(nodeName);
             attribs.setQualifiedName(nodeName + "-" + appliName);
             attribs.setStartTime(now);
             attribs.setVersion(appVersion);
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
                 if (DatagouvMLSConstants.dataStoreTypes.keySet().contains(relationships.get(nrel).getRequirementType())) {

                    log.info ("Processing relation " + relationships.get(nrel).getRequirementType() + " with target " + relationships.get(nrel).getTarget());

                    /* get datastore service node */
                    NodeTemplate serviceNode = nodeTemplates.get(relationships.get(nrel).getTarget());
                    serviceNodes.add(serviceNode);

                    try {
                       DataStore ds = (DataStore)DatagouvMLSConstants.dataStoreTypes.get(relationships.get(nrel).getRequirementType()).newInstance();
                       servicesToDs.put (serviceNode.getName(), ds);
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
          String json = (new ObjectMapper()).writeValueAsString(fullAppli);
          log.debug("JSON=" + json);

          Path path = Files.createTempFile("dgv", ".json");
          Files.write(path, json.getBytes(StandardCharsets.UTF_8));

          String[] commands = new String[10];
          commands[0] = "curl";
          commands[1] = "-k";
          commands[2] = "-X";
          commands[3] = "POST";
          commands[4] = "-u";
          commands[5] = configuration.getApplicationPostCredentials();
          commands[6] = "-d@" + path.toFile().getAbsolutePath();
          commands[7] = "-H";
          commands[8] = "Content-type: application/json";
          commands[9] = configuration.getApplicationPostUrl();
          StringBuffer output = new StringBuffer();
          StringBuffer error = new StringBuffer();

          int ret = ProcessLauncher.launch(commands, output, error);
          Files.delete (path);
          if (ret != 0) {
             log.error ("Error " + ret +" [" + error.toString() + "]");
          } else {
             log.debug("RESPONSE=" + output.toString());
             Application retAppli = (new ObjectMapper()).readValue(output.toString(), Application.class);

             if ((retAppli.getEntities() == null) || (retAppli.getEntities().size() == 0)) {
                log.error ("DataGouv response contains no entity !");
             } else for (Entity retEntity : retAppli.getEntities()) {
                if (retEntity.getTypeName().equals(DatagouvMLSConstants.MODULE_INSTANCE_NAME)) {

                   List<NodeTemplate> serviceNodes = nodesToServices.get(retEntity.getAttributes().getName());
                   if (serviceNodes == null) {
                      log.warn("Can not find services for " + retEntity.getAttributes().getName());
                   } else {
                      /* update inputs for create operation */
                      Operation createOp = TopologyUtils.getCreateOperation(nodeTemplates.get(retEntity.getAttributes().getName()));

                      if (createOp != null) {
                         for (NodeTemplate service : serviceNodes) {
                            Map<String, String> newvals = new HashMap<String,String>();
                            safe(createOp.getInputParameters()).forEach((inputName, iValue) -> {
                               /* update inputs for each service: concats are directly updated, get_property's return new value */
                               String val = TopologyUtils.updateInput (nodeTemplates.get(retEntity.getAttributes().getName()), 
                                                                       servicesToDs.get(service.getName()), inputName, 
                                                                       (AbstractPropertyValue)iValue,
                                                                       retEntity.getAttributes().getTokenid(), retEntity.getAttributes().getPwdid());
                               if (val != null) {
                                  newvals.put (inputName, val);
                               }
                            });

                            newvals.forEach((inputName, value) -> {
                               createOp.getInputParameters().put (inputName, new ScalarPropertyValue(value));
                            });
                         }
                      }
                   }
                }
             }
          }

          /* send request to getPds */
          commands = new String[5];
          commands[0] = "curl";
          commands[1] = "-k";
          commands[2] = "-u";
          commands[3] = configuration.getGetPdsCredentials();
          commands[4] = configuration.getGetPdsUrl() + URLEncoder.encode(appliName, StandardCharsets.UTF_8.toString());
          output = new StringBuffer();
          error = new StringBuffer();

          ret = ProcessLauncher.launch(commands, output, error);
          if (ret != 0) {
             log.error ("Error " + ret +"[" + error.toString() + "]");
          } else {
             log.debug ("GET PDS RESPONSE=" + output.toString());
             Application retAppli = (new ObjectMapper()).readValue(output.toString(), Application.class);

             if ((retAppli.getEntities() == null) || (retAppli.getEntities().size() == 0)) {
                log.error ("DataGouv GetPds response contains no entity !");
             } else {
                Entity pAppli = retAppli.getEntities().get(0);
                if ((pAppli.getTypeName() == null) || (!pAppli.getTypeName().equals(DatagouvMLSConstants.APPLI_NAME))) {
                   log.error ("DataGouv response contains no " + DatagouvMLSConstants.APPLI_NAME + "!");
                } else if ((pAppli.getClassifications() == null) || 
                           (pAppli.getClassifications().size() == 0)) {
                   log.info ("DataGouv GetPds response contains no " + DatagouvMLSConstants.CLASSIFICATION_NAME);
                } else {
                   String level = null;
                   boolean found = false;
                   Iterator<Classification> iter = pAppli.getClassifications().iterator();

                   while (iter.hasNext() && !found) {
                      Classification classif = iter.next();
                      if (classif.getTypeName().equals(DatagouvMLSConstants.CLASSIFICATION_NAME)) {
                         found = true;
                         level = classif.getAttributes().getLevel();
                      }
                   }

                   if (!found) {
                      log.info ("DataGouv GetPds response contains no " + DatagouvMLSConstants.CLASSIFICATION_NAME);
                   } else {
                      processPds (topology, context, level);
                   }
                }
             }
          }

       } catch (Exception e) {
          log.error ("Got exception:" + e.getMessage(), e);
       }
    }

    private void processPds (Topology topology, FlowExecutionContext context, String level) {

       /* create namespace node to be processed bu kubernetes plugin */
       NodeTemplate kubeNSNode = addNodeTemplate(null, topology, "Namespace", K8S_TYPES_KUBE_NAMESPACE, K8S_CSAR_VERSION);

       /* get "Cas d'usage" from meta property */
       String cuname = null;
       String cuNameMetaPropertyKey = this.metaPropertiesService.getMetapropertykeyByName(CUNAME_PROP, MetaPropertyTarget.APPLICATION);

       if (cuNameMetaPropertyKey != null) {
          Optional<EnvironmentContext> ec = context.getEnvironmentContext();
          if (ec.isPresent() && cuNameMetaPropertyKey != null) {
             EnvironmentContext env = ec.get();
             Map<String, String> metaProperties = safe(env.getApplication().getMetaProperties());
             String sCuname = metaProperties.get(cuNameMetaPropertyKey);
             if ((sCuname!=null) && !sCuname.equals("")) {
                 cuname = sCuname;
             }
         }
       }
       if (cuname == null) {
          log.warn ("Can not find " + CUNAME_PROP);
          cuname = "default";
       }

       /* generate namespace name */
       String namespace = ("cu-p-" + context.getEnvironmentContext().get().getEnvironment().getName() + "-" + cuname + 
                          "-" + context.getEnvironmentContext().get().getApplication().getName()).toLowerCase();
       setNodePropertyPathValue(null, topology, kubeNSNode, "namespace", new ScalarPropertyValue(namespace));
       setNodePropertyPathValue(null, topology, kubeNSNode, "apiVersion", new ScalarPropertyValue("v1"));

       /* build metadata with annotation for env from PDS level */
       Map<String,Object> metadata = new HashMap<String,Object>();
       Map<String,Object> annotations = new HashMap<String,Object>();
       annotations.put ("scheduler.alpha.kubernetes.io/node-selector", new ScalarPropertyValue("env=" + level.toLowerCase()));
       metadata.put("annotations", annotations);
       setNodePropertyPathValue(null, topology, kubeNSNode, "metadata", new ComplexPropertyValue(metadata));

    }
}
