package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.model.common.Tag;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.topology.task.RedirectionTask;
import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.CloneUtil;
import alien4cloud.utils.PropertyUtil;

import org.alien4cloud.alm.deployment.configuration.flow.EnvironmentContext;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.CSARDependency;
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
import javax.inject.Inject;
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

    @Inject
    private DeploymentRuntimeStateService deploymentRuntimeStateService;

    @Inject
    private DatagouvMLSListener dgvListener;

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
       
       /* check whether application/environment is deployed or not */
       try {
          if (deploymentRuntimeStateService.getRuntimeTopologyFromEnvironment(context.getEnvironmentContext().get().getEnvironment().getId()).isDeployed()) {
             log.info ("Topology is deployed, nothing to do.");
             return;
          }
       }
       catch (NotFoundException e) {} // not deployed yet

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
          NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplates.get(nodeName).getType());

          String typeCompo = getMetaprop(nodeType, DatagouvMLSConstants.COMPONENT_TYPE);
          if ((typeCompo!=null) && typeCompo.equalsIgnoreCase("Module")) {
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

       boolean gotPds = false;
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

                   NodeTemplate node = topology.getNodeTemplates().get(retEntity.getAttributes().getName());
                   if (node != null) {
                      Tag tkTag = new Tag();
                      tkTag.setName(DatagouvMLSConstants.TOKEN_TAGNAME);
                      tkTag.setValue(retEntity.getAttributes().getTokenid());
                      List<Tag> tags = node.getTags();
                      if (tags == null) {
                         tags = new ArrayList<Tag>();
                      }
                      tags.add(tkTag);
                      node.setTags(tags);
                   } else {
                       log.error ("Cannot find module " + retEntity.getAttributes().getName());
                   }

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
          context.getExecutionCache().put(FlowExecutionContext.INITIAL_TOPOLOGY, CloneUtil.clone(topology));

          /* store application description to be used by DataGouvMLSListener on validatation phase */
          dgvListener.storeAppli (appliName, fullAppli);

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
             Pds pds = (new ObjectMapper()).readValue(output.toString(), Pds.class);

             if ((pds.getErreur() != null) && !pds.getErreur().trim().equals("")) { 
                log.error ("DataGouv GetPds error: " + pds.getErreur());
            } else if ((pds.getZone() == null) || pds.getZone().trim().equals("")) {
                log.error ("DataGouv GetPds response contains no zone!");
            } else {
                processPds (topology, context, pds.getZone());
                gotPds = true;
             }
          }

       } catch (Exception e) {
          log.error ("Got exception:" + e.getMessage(), e);
       }

       if (!gotPds) {
          String url = configuration.getPdsIhmUrl() + appliName;
          log.info("Redirection URL: " + url);
          context.log().warn("Please use the following URL to continue : " + url);
          context.log().error(new RedirectionTask(url, "urlRetour"));
       }
    }

    private void processPds (Topology topology, FlowExecutionContext context, String level) {

       if (!TopologyUtils.isK8S(topology)) {
          return;
       }

       /* create namespace node to be processed bu kubernetes plugin */
       NodeTemplate kubeNSNode = addNodeTemplate(null, topology, "Namespace", K8S_TYPES_KUBE_NAMESPACE, getK8SCsarVersion(topology));

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
                          "--" + context.getEnvironmentContext().get().getApplication().getName()).toLowerCase();
       setNodePropertyPathValue(null, topology, kubeNSNode, "namespace", new ScalarPropertyValue(namespace));
       setNodePropertyPathValue(null, topology, kubeNSNode, "apiVersion", new ScalarPropertyValue("v1"));

       /* build metadata with annotation for env from PDS level */
       Map<String,Object> metadata = new HashMap<String,Object>();
       Map<String,Object> labels = new HashMap<String,Object>();
       labels.put ("ns-zone-de-sensibilite", new ScalarPropertyValue(level.toLowerCase()));
       labels.put ("ns-artemis-role", new ScalarPropertyValue("cas-usage"));
       labels.put ("ns-pf-role", new ScalarPropertyValue("cas-usage"));
       labels.put ("ns-clef-namespace", new ScalarPropertyValue(namespace));
       Map<String,Object> annotations = new HashMap<String,Object>();
       annotations.put ("scheduler.alpha.kubernetes.io/node-selector", new ScalarPropertyValue("ns-zone-de-sensibilite=" + level.toLowerCase()));
       metadata.put("annotations", annotations);
       metadata.put("labels", labels);
       setNodePropertyPathValue(null, topology, kubeNSNode, "metadata", new ComplexPropertyValue(metadata));

    }

    private String getK8SCsarVersion(Topology topology) {
        for (CSARDependency dep : topology.getDependencies()) {
            if (dep.getName().equals("org.alien4cloud.kubernetes.api")) {
                return dep.getVersion();
            }
        }
        return K8S_CSAR_VERSION;
    }

    private String getMetaprop (NodeType node, String prop) {
       String propKey = metaPropertiesService.getMetapropertykeyByName(prop, MetaPropertyTarget.COMPONENT);

       if (propKey != null) {
          return safe(node.getMetaProperties()).get(propKey);
       }

       return null;
    }

}
