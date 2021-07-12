package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.model.common.Tag;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.orchestrators.locations.services.LocationService;
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
import org.alien4cloud.alm.deployment.configuration.model.DeploymentMatchingConfiguration;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.alien4cloud.tosca.utils.ToscaTypeUtils;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.utils.ProcessLauncher;
import org.alien4cloud.plugin.datagouv_mls.utils.TopologyUtils;
import org.alien4cloud.plugin.datagouv_mls.datastore.DataStore;
import org.alien4cloud.plugin.datagouv_mls.datastore.PV;
import org.alien4cloud.plugin.datagouv_mls.model.*;

import static org.alien4cloud.plugin.kubernetes.csar.Version.K8S_CSAR_VERSION;
import static org.alien4cloud.plugin.kubernetes.modifier.KubernetesAdapterModifier.K8S_TYPES_KUBE_NAMESPACE;
import static org.alien4cloud.plugin.kubernetes.modifier.KubernetesAdapterModifier.K8S_TYPES_KUBECONTAINER;
import static org.alien4cloud.plugin.kubernetes.modifier.KubernetesAdapterModifier.K8S_TYPES_KUBE_SERVICE;
import static org.alien4cloud.plugin.kubernetes.modifier.KubeTopologyUtils.K8S_TYPES_VOLUMES_CLAIM_SC;
import static alien4cloud.plugin.k8s.spark.jobs.modifier.SparkJobsModifier.K8S_TYPES_SPARK_JOBS;

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
import java.util.Set;

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
    private LocationService locationService;

    @Inject
    private DatagouvMLSListener dgvListener;

    private int guid = -1;

    private String getGuid() {
        String ret = String.valueOf(guid);
        guid--;
        return ret;
    }

    private static final String CUNAME_PROP = "Cas d'usage";
    private static final String BAS_PROP = "Bac à sable";

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            log.warn("Couldn't process Datagouv-MLS modifier, got " + e.getMessage());
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {

        /* check whether application/environment is deployed or not */
        try {
            if (deploymentRuntimeStateService.getRuntimeTopologyFromEnvironment(context.getEnvironmentContext().get().getEnvironment().getId()).isDeployed()) {
                log.info("Topology is deployed, nothing to do.");
                return;
            }
        } catch (NotFoundException e) {
        } // not deployed yet

        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
        Map<String, NodeTemplate> modules = new HashMap<String, NodeTemplate>();
        safe(nodeTemplates).forEach ((nodeName, node) -> {
            NodeType nodeType = ToscaContext.get(NodeType.class, node.getType());

            String typeCompo = getMetaprop(nodeType, DatagouvMLSConstants.COMPONENT_TYPE);
            if ((typeCompo != null) && typeCompo.equalsIgnoreCase("Module")) {
               modules.put (nodeName, node);
            }
        });

        /* get "Cas d'usage" from meta property */
        String cuname = null;
        String cuNameMetaPropertyKey = this.metaPropertiesService.getMetapropertykeyByName(CUNAME_PROP, MetaPropertyTarget.APPLICATION);

        if (cuNameMetaPropertyKey != null) {
            Optional<EnvironmentContext> ec = context.getEnvironmentContext();
            if (ec.isPresent() && cuNameMetaPropertyKey != null) {
                EnvironmentContext env = ec.get();
                Map<String, String> metaProperties = safe(env.getApplication().getMetaProperties());
                String sCuname = metaProperties.get(cuNameMetaPropertyKey);
                if ((sCuname != null) && !sCuname.equals("")) {
                    cuname = sCuname;
                }
            }
        }
        if (cuname == null) {
            log.warn("Can not find " + CUNAME_PROP);
            cuname = "default";
        }

        String appliName = "L_ACU_" + context.getEnvironmentContext().get().getEnvironment().getEnvironmentType() + "-" + 
                           context.getEnvironmentContext().get().getEnvironment().getName() + "-" +
                           cuname + "--" + context.getEnvironmentContext().get().getApplication().getId();

        if (modules.isEmpty()) {
           log.info("No modules, nothing to do.");
           dgvListener.storeExemptedAppli(context.getEnvironmentContext().get().getApplication().getName() + "-" + 
                                          context.getEnvironmentContext().get().getEnvironment().getName());
           return;
        }

        Optional<DeploymentMatchingConfiguration> configurationOptional = context.getConfiguration(DeploymentMatchingConfiguration.class,
                DatagouvMLSModifier.class.getSimpleName());

        if (!configurationOptional.isPresent()) {
            log.warn("Cannot get DeploymentMatchingConfiguration");
        }
        DeploymentMatchingConfiguration matchingConfiguration = configurationOptional.get();
        String locationId = matchingConfiguration.getLocationIds().get("_A4C_ALL");
        if (!isSet(locationId)) {
            log.warn("Cannot get location");
        }
        Location location = locationService.getOrFail(locationId);
        String basMetaPropertyKey = this.metaPropertiesService.getMetapropertykeyByName(BAS_PROP, MetaPropertyTarget.LOCATION);
        String sBas = "false";
        if (basMetaPropertyKey == null) {
            log.warn("{} metaproperty does not exist", BAS_PROP);
        } else {
           sBas = safe(location.getMetaProperties()).get(basMetaPropertyKey);
           if (sBas == null) {
              log.info("{} metaproperty not set on location, using false", BAS_PROP);
              sBas = "false";
           } else {
              log.debug("{}:{}", BAS_PROP, sBas);
           }
        }

        Pds pds = null;
        try {
            /* send request to getPds */
            String[] commands = new String[5];
            commands[0] = "curl";
            commands[1] = "-k";
            commands[2] = "-u";
            commands[3] = configuration.getGetPdsCredentials();
            commands[4] = configuration.getGetPdsUrl() + URLEncoder.encode(appliName, StandardCharsets.UTF_8.toString());
            StringBuffer output = new StringBuffer();
            StringBuffer error = new StringBuffer();

            log.debug ("Checking for existing PDS");
            int ret = ProcessLauncher.launch(commands, output, error);
            if (ret != 0) {
                log.error("Error " + ret + "[" + error.toString() + "]");
            } else {
                log.debug("GET PDS RESPONSE=" + output.toString());
                Pds respPds = (new ObjectMapper()).readValue(output.toString(), Pds.class);
    
                if (isSet(respPds.getPds()) && isSet(respPds.getZone())) {
                   pds = respPds;
                   updateServices (topology, pds);
                }
            }
        } catch (Exception e) {
            log.error("Got exception:" + e.getMessage(), e);
        }

        Tag apqnTag = new Tag();
        apqnTag.setName(DatagouvMLSConstants.QN_TAGNAME);
        apqnTag.setValue(appliName);
        List<Tag> aptags = topology.getTags();
        if (aptags == null) {
          aptags = new ArrayList<Tag>();
        }
        aptags.add(apqnTag);
        topology.setTags(aptags);

        String now = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(new Date()).toString();
        guid = -1;
        String appliId = getGuid();
        String appVersion = context.getEnvironmentContext().get().getEnvironment().getVersion();

        /* application : list of entities & referredEntities */
        Application fullAppli = new Application();
        List<Entity> entities = new ArrayList<Entity>();
        Map<String, Entity> referredEntities = new HashMap<String, Entity>();
        fullAppli.setEntities(entities);
        fullAppli.setReferredEntities(referredEntities);

        /* first entity describes application */
        Entity appli = new Entity();
        appli.setTypeName(DatagouvMLSConstants.APPLI_NAME);
        Attributes aattribs = new Attributes();
        aattribs.setName(context.getEnvironmentContext().get().getApplication().getName());
        aattribs.setQualifiedName(appliName);
        aattribs.setVersion(appVersion);
        aattribs.setStartTime(now);
        aattribs.setStatus("PROPOSED");
        aattribs.setBacASable(sBas);
        appli.setAttributes(aattribs);
        appli.setGuid(appliId);

        entities.add(appli);

        /* maps to store links between modules and services nodes, and services and datastores objects */
        Map<String, List<NodeTemplate>> nodesToServices = new HashMap<String, List<NodeTemplate>>();
        Map<String, DataStore> servicesToDs = new HashMap<String, DataStore>();

        /* process nodes */
        modules.forEach ( (nodeName, node) -> {
                NodeType nodeType = ToscaContext.get(NodeType.class, node.getType());
                String version = nodeType.getArchiveVersion();
                log.info("Processing node " + nodeName);

                Tag qnTag = new Tag();
                qnTag.setName(DatagouvMLSConstants.QN_TAGNAME);
                qnTag.setValue(appliName + "-" + nodeName);
                List<Tag> tags = node.getTags();
                if (tags == null) {
                    tags = new ArrayList<Tag>();
                }
                tags.add(qnTag);
                node.setTags(tags);

                List<NodeTemplate> serviceNodes = new ArrayList<NodeTemplate>();
                nodesToServices.put(nodeName, serviceNodes);

                String moduleGuid = getGuid();

                /* module entity descriptor */
                Entity module = new Entity();
                module.setTypeName(DatagouvMLSConstants.MODULE_INSTANCE_NAME);
                Attributes attribs = new Attributes();
                attribs.setName(nodeName);
                attribs.setQualifiedName(appliName + "-" + nodeName);
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
                Map<String, RelationshipTemplate> relationships = node.getRelationships();
                List<Entity> inputs = new ArrayList<Entity>();
                List<Entity> outputs = new ArrayList<Entity>();
                for (String nrel : safe(relationships).keySet()) {
                    /* datastores relations are in dataStoreTypes map */
                    if (DatagouvMLSConstants.dataStoreTypes.keySet().contains(relationships.get(nrel).getRequirementType())) {

                        log.info("Processing relation " + relationships.get(nrel).getRequirementType() + " with target " + relationships.get(nrel).getTarget());

                        /* get datastore service node */
                        NodeTemplate serviceNode = nodeTemplates.get(relationships.get(nrel).getTarget());
                        serviceNodes.add(serviceNode);

                        try {
                            DataStore ds = (DataStore) DatagouvMLSConstants.dataStoreTypes.get(relationships.get(nrel).getRequirementType()).newInstance();
                            ds.setConfiguration(configuration);
                            servicesToDs.put(serviceNode.getName(), ds);
                            String typeName = ds.getTypeName();
                            /* get number of first level elements for datastore in order to generate inputs & outputs */
                            int nb = ds.getNbSets(relationships.get(nrel).getProperties());
                            int startGuid = guid;

                            /* generate inputs & outputs according to number of first level elements returned by datastore */
                            for (int cur = 0; cur < nb; cur++) {
                                Entity xput = new Entity();
                                xput.setTypeName(typeName);
                                xput.setGuid(getGuid());
                                inputs.add(xput);
                                outputs.add(xput);
                            }

                            /* generate reference entities for this datastore */
                            Map<String, Entity> dsEntities = ds.getEntities(relationships.get(nrel).getProperties(), serviceNode, startGuid, guid, context);
                            guid -= (dsEntities.size() - nb);

                            referredEntities.putAll(dsEntities);

                            /* process PV if any */
                            if (ds instanceof PV) {
                              processPV (topology, node, appliName + "-" + nodeName, relationships.get(nrel), serviceNode);
                            }
                        } catch (Exception e) {
                            log.error("Got exception : " + e.getMessage());
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
                attribs.setName(qname.substring(qname.lastIndexOf(".") + 1));
                attribs.setQualifiedName(qname);
                attribs.setVersion(version);
                refModule.setAttributes(attribs);

                referredEntities.put(moduleGuid, refModule);
        });

        boolean needRedirect = false;
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

            int ret = -1;
            synchronized(this) {
               ret = ProcessLauncher.launch(commands, output, error);
            }
            Files.delete(path);
            if (ret != 0) {
                log.error("Error " + ret + " [" + error.toString() + "]");
            } else {
                log.debug("RESPONSE=" + output.toString());
                Application retAppli = (new ObjectMapper()).readValue(output.toString(), Application.class);

                if ((retAppli.getEntities() == null) || (retAppli.getEntities().size() == 0)) {
                    log.error("DataGouv response contains no entity !");
                    if (retAppli.getMessage() != null) {
                       log.error("Error while declaring application: " + retAppli.getMessage() + " [" + retAppli.getCode() + "]");
                       context.log().error("Error while declaring application: " + retAppli.getMessage() +
                                            (isSet(retAppli.getCode()) ? " [" + retAppli.getCode() + "]" : ""));
                    }
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
                            log.error("Cannot find module " + retEntity.getAttributes().getName());
                        }

                        List<NodeTemplate> serviceNodes = nodesToServices.get(retEntity.getAttributes().getName());
                        if (serviceNodes == null) {
                            log.warn("Can not find services for " + retEntity.getAttributes().getName());
                        } else {
                            NodeType nodeType = ToscaContext.get(NodeType.class, node.getType());

                            if (ToscaTypeUtils.isOfType(nodeType, K8S_TYPES_KUBECONTAINER)) {
                                /* update inputs for create operation */
                                Operation createOp = TopologyUtils.getCreateOperation(nodeTemplates.get(retEntity.getAttributes().getName()));

                                if (createOp != null) {
                                    for (NodeTemplate service : serviceNodes) {
                                        Map<String, String> newvals = new HashMap<String, String>();
                                        safe(createOp.getInputParameters()).forEach((inputName, iValue) -> {
                                            /* update inputs for each service: concats are directly updated, get_property's return new value */
                                            String val = TopologyUtils.updateInput(nodeTemplates.get(retEntity.getAttributes().getName()),
                                                    servicesToDs.get(service.getName()), inputName,
                                                    (AbstractPropertyValue) iValue,
                                                    retEntity.getAttributes().getTokenid(), retEntity.getAttributes().getPwdid());
                                            if (val != null) {
                                                newvals.put(inputName, val);
                                            }
                                        });

                                        newvals.forEach((inputName, value) -> {
                                            createOp.getInputParameters().put(inputName, new ScalarPropertyValue(value));
                                        });
                                    }
                                }
                            } else if (ToscaTypeUtils.isOfType(nodeType, K8S_TYPES_SPARK_JOBS)) {

                                if (log.isDebugEnabled()) {
                                    log.debug("Processing new fashion K8S spark Job for node {}", node.getName());
                                }

                                AbstractPropertyValue varNamesPv = node.getProperties().get(DatagouvMLSConstants.VAR_VALUES_PROPERTY);
                                if (varNamesPv != null && varNamesPv instanceof ComplexPropertyValue) {

                                    if (log.isDebugEnabled()) {
                                        log.debug("Found property {}", DatagouvMLSConstants.VAR_VALUES_PROPERTY);
                                    }

                                    Map<String, Object> varValues = ((ComplexPropertyValue) varNamesPv).getValue();
                                    processNode(topology, node, varValues, retEntity.getAttributes());
                                }
                            }
                        }
                    }
                }
            }
            context.getExecutionCache().put(FlowExecutionContext.INITIAL_TOPOLOGY, CloneUtil.clone(topology));

            if (pds == null) {
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
                    log.error("Error " + ret + "[" + error.toString() + "]");
                } else {
                    log.debug("GET PDS RESPONSE=" + output.toString());
                    pds = (new ObjectMapper()).readValue(output.toString(), Pds.class);
                }
            }

            if (pds != null) {
                if (isSet(pds.getErreurZone())) {
                    log.error("DataGouv GetPds error: " + pds.getErreurZone());
                    needRedirect = true;
                } else if (!isSet(pds.getZone())) {
                    log.error("DataGouv GetPds response contains no zone!");
                    if (isSet(pds.getMessage())) {
                       log.error("DataGouv GetPds error: " + pds.getMessage() + " [" + pds.getCode() + "]");
                       context.log().error("Error while getting PDS: " + pds.getMessage() +
                                            (isSet(pds.getCode()) ? " [" + pds.getCode() + "]" : ""));
                    } else {
                       context.log().error("Error while getting PDS");
                    }
                } else {
                    processPds(topology, context, pds, pds.getZone(), appliName);
                    context.log().info("Using PDS {}, zone {}", isSet(pds.getPds()) ? pds.getPds(): "<not set>", pds.getZone());

                    /* store application description to be used by DataGouvMLSListener on validatation phase */
                    dgvListener.storeAppli(context.getEnvironmentContext().get().getApplication().getName() + "-" + 
                                           context.getEnvironmentContext().get().getEnvironment().getName(), fullAppli, pds);
                }
            }

        } catch (Exception e) {
            log.error("Got exception:" + e.getMessage(), e);
        }

        if (needRedirect) {
            String url = configuration.getPdsIhmUrl() + appliName;
            log.info("Redirection URL: " + url);
            context.log().warn("Please use the following URL to continue : " + url);
            context.log().error(new RedirectionTask(url, "urlRetour"));
        }
    }

    private void processPds(Topology topology, FlowExecutionContext context, Pds pds, String level, String appli) {

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
                if ((sCuname != null) && !sCuname.equals("")) {
                    cuname = sCuname;
                }
            }
        }
        if (cuname == null) {
            log.warn("Can not find " + CUNAME_PROP);
            cuname = "default";
        }

        /* generate namespace name */
        String namespace = ("cu-p-" + context.getEnvironmentContext().get().getEnvironment().getName() + "-" + cuname +
                "--" + context.getEnvironmentContext().get().getApplication().getName()).toLowerCase() + "-" + level;
        setNodePropertyPathValue(null, topology, kubeNSNode, "namespace", new ScalarPropertyValue(namespace.toLowerCase().replaceAll("_","-")));
        setNodePropertyPathValue(null, topology, kubeNSNode, "apiVersion", new ScalarPropertyValue("v1"));

        /* build metadata with annotation for env from PDS level */
        Map<String, Object> metadata = new HashMap<String, Object>();
        Map<String, Object> labels = new HashMap<String, Object>();
        labels.put("ns-zone-de-sensibilite", new ScalarPropertyValue(level.toLowerCase()));
        labels.put("ns-artemis-role", new ScalarPropertyValue("cas-usage"));
        labels.put("ns-pf-role", new ScalarPropertyValue("cas-usage"));
        labels.put("ns-clef-namespace", new ScalarPropertyValue(namespace));
        Map<String, Object> annotations = new HashMap<String, Object>();
        annotations.put("scheduler.alpha.kubernetes.io/node-selector", new ScalarPropertyValue("zone-de-sensibilite=" + level.toLowerCase()));
        metadata.put("annotations", annotations);
        metadata.put("labels", labels);
        setNodePropertyPathValue(null, topology, kubeNSNode, "metadata", new ComplexPropertyValue(metadata));

        /* update zone and pds in services */
        updateServices (topology, pds);

        /* update zone and pds in spark jobs nodes */
        updateJobNodes (topology, pds);

        /* process modules if any */
        if ((pds.getModules() == null) || (pds.getModules().size() == 0)) {
           return;
        }
        for (org.alien4cloud.plugin.datagouv_mls.model.Module module : pds.getModules()) {
           if (module.getName() == null) {
              log.error ("Module has no name: " + module.toString());
           } else {
              String nodeName = module.getName();
              log.debug ("Processing PDS for node " + nodeName);
              NodeTemplate node = topology.getNodeTemplates().get(nodeName);
              if (node != null) {
                 if (isSet(module.getPdsModuleInstancie())) {
                    try {
                       setNodePropertyPathValue(null, topology, node, "pdsModuleInstancie", new ScalarPropertyValue(module.getPdsModuleInstancie()));
                    } catch (Exception e) {
                       log.debug ("Can not set pdsModuleInstancie for " + nodeName + " (" + e.getMessage() + ")");
                    }
                 }
                 if (isSet(module.getPdsEcritureModuleInstancie())) {
                    try {
                       setNodePropertyPathValue(null, topology, node, "pdsEcritureModuleInstancie", new ScalarPropertyValue(module.getPdsEcritureModuleInstancie()));
                    } catch (Exception e) {
                       log.debug ("Can not set pdsEcritureModuleInstancie for " + nodeName + " (" + e.getMessage() + ")");
                    }
                 }
                 if (isSet(module.getPdsModuleImporte())) {
                    try {
                       setNodePropertyPathValue(null, topology, node, "pdsModuleImporte", new ScalarPropertyValue(module.getPdsModuleImporte()));
                    } catch (Exception e) {
                       log.debug ("Can not set pdsModuleImporte for " + nodeName + " (" + e.getMessage() + ")");
                    }
                 }
                 if (module.getPdsModuleInstancieNum() != null) {
                    try {
                       setNodePropertyPathValue(null, topology, node, "pdsModuleInstancieNum", new ScalarPropertyValue(module.getPdsModuleInstancieNum().toString()));
                    } catch (Exception e) {
                       log.debug ("Can not set pdsModuleInstancieNum for " + nodeName + " (" + e.getMessage() + ")");
                    }
                 }
                 if (module.getPdsEcritureModuleInstancieNum() != null) {
                    try {
                       setNodePropertyPathValue(null, topology, node, "pdsEcritureModuleInstancieNum", new ScalarPropertyValue(module.getPdsEcritureModuleInstancieNum().toString()));
                    } catch (Exception e) {
                       log.debug ("Can not set pdsEcritureModuleInstancieNum for " + nodeName + " (" + e.getMessage() + ")");
                    }
                 }
                 if (module.getPdsModuleImporteNum() != null) {
                    try {
                       setNodePropertyPathValue(null, topology, node, "pdsModuleImporteNum", new ScalarPropertyValue(module.getPdsModuleImporteNum().toString()));
                    } catch (Exception e) {
                       log.debug ("Can not set pdsModuleImporteNum for " + nodeName + " (" + e.getMessage() + ")");
                    }
                 }

              } else {
                 log.error("Cannot find module " + module.getName());
              }
           }
        }

    }

    private String getK8SCsarVersion(Topology topology) {
        for (CSARDependency dep : topology.getDependencies()) {
            if (dep.getName().equals("org.alien4cloud.kubernetes.api")) {
                return dep.getVersion();
            }
        }
        return K8S_CSAR_VERSION;
    }

    private String getMetaprop(NodeType node, String prop) {
        String propKey = metaPropertiesService.getMetapropertykeyByName(prop, MetaPropertyTarget.COMPONENT);

        if (propKey != null) {
            return safe(node.getMetaProperties()).get(propKey);
        }

        return null;
    }

    private void processNode(Topology topology, NodeTemplate clientNode, Map<String, Object> varValues, Attributes credential) {
        // all relationship to datastores should use these credentials
        if (log.isDebugEnabled()) {
            log.debug("Processing node {}, exploring relationships", clientNode.getName());
        }

        Set<RelationshipTemplate> relationships = TopologyNavigationUtil.getRelationshipsFromType(clientNode, DatagouvMLSConstants.RELATIONSHIP_TYPE_TO_EXPLORE);
        if (log.isDebugEnabled()) {
            log.debug("Found {} relationship of type {} for node {}", relationships.size(), DatagouvMLSConstants.RELATIONSHIP_TYPE_TO_EXPLORE, clientNode.getName());
        }

        relationships.stream().forEach(relationshipTemplate -> {
            if (log.isDebugEnabled()) {
                log.debug("Processing relationship {} for node {}", relationshipTemplate.getName(), clientNode.getName());
            }
            NodeTemplate targetNode = topology.getNodeTemplates().get(relationshipTemplate.getTarget());
            AbstractPropertyValue apv = relationshipTemplate.getProperties().get(DatagouvMLSConstants.VAR_MAPPING_PROPERTY);
            if (apv != null && apv instanceof ComplexPropertyValue) {
                Map<String, Object> mappingProperties = ((ComplexPropertyValue) apv).getValue();
                processCredential(mappingProperties, "username", credential.getTokenid(), varValues);
                processCredential(mappingProperties, "password", credential.getPwdid(), varValues);
            }
        });
    }

    private void processCredential(Map<String, Object> mappingProperties, String propertyName, String credentialValue, Map<String, Object> varValues) {
        Object usernameObj = mappingProperties.get(propertyName);
        if (usernameObj != null) {
            String varNames = usernameObj.toString();
            String[] varNamesArray = varNames.split(",");
            for (String varName : varNamesArray) {
                varValues.put(varName, new ScalarPropertyValue(credentialValue));
            }
        }
    }

    private boolean isSet(String val) {
       return (val != null) && !val.trim().equals("");
    }

    private void updateServices (Topology topology, Pds pds) {
        safe(topology.getNodeTemplates()).forEach ((nodeName, node) -> {
            if (node instanceof ServiceNodeTemplate) {
               updateService ((ServiceNodeTemplate)node, pds);
            }
        });
    }

    private void updateService (ServiceNodeTemplate node, Pds pds) {
       log.debug ("Updating service {}", node.getName());
       /* update properties */
       updatePdsVars(node.getProperties(), pds).forEach ((name, value) -> {
          node.getProperties().put (name, new ScalarPropertyValue (value));
          log.debug ("Setting property {} to {}", name, value);
       });
       /* update capabilities */
       safe(node.getCapabilities()).forEach ((nameC, capa) -> {
          updatePdsVars(capa.getProperties(), pds).forEach ((nameP, value) -> {
             capa.getProperties().put (nameP, new ScalarPropertyValue (value));
             log.debug ("Setting capability {}, property {} to {}", nameC, nameP, value);
          });
       });
       /* update attributes */
       updatePdsVars(node.getAttributeValues(), pds).forEach ((name, value) -> {
          node.getAttributeValues().put (name, value);
          log.debug ("Setting attribute {} to {}", name, value);
       });
    }

    private Map<String,String> updatePdsVars (Map props, Pds pds) {
       final Map<String, String> newvals = new HashMap<String, String>();
       safe(props).forEach ((name, valueObj) -> {
          String value;
          if (valueObj instanceof ScalarPropertyValue) {
             value = ((ScalarPropertyValue)valueObj).getValue();
          } else if (valueObj instanceof String) {
             value = (String)valueObj;
          } else {
             return; // skips this iteration
          }
          boolean modified = false;
          if (value.indexOf("<zone>") != -1) {
             modified = true;
             value = value.replaceAll("<zone>", pds.getZone());
          }
          if ((value.indexOf("<pds>") != -1) && isSet(pds.getPds())) {
             modified = true;
             value = value.replaceAll("<pds>", pds.getPds());
          }
          if (modified) {
             newvals.put((String)name, value);
          }
       });
       return newvals;
    }       

    private void updateJobNodes (Topology topology, Pds pds) {
        safe(topology.getNodeTemplates()).forEach ((nodeName, node) -> {
            NodeType nodeType = ToscaContext.get(NodeType.class, node.getType());
            if (ToscaTypeUtils.isOfType(nodeType, K8S_TYPES_SPARK_JOBS)) {
               updateJobNode (node, pds);
            }
        });
    }

    private void updateJobNode (NodeTemplate node, Pds pds) {
       log.debug ("Updating node {}", node.getName());
       AbstractPropertyValue varNamesPv = node.getProperties().get(DatagouvMLSConstants.VAR_VALUES_PROPERTY);
       if (varNamesPv != null && varNamesPv instanceof ComplexPropertyValue) {
          Map<String, Object> varValues = ((ComplexPropertyValue) varNamesPv).getValue();

          updatePdsVars(varValues, pds).forEach ((name, value) -> {
             varValues.put (name, new ScalarPropertyValue(value));
             log.debug ("Setting value {} to {}", name, value);
          });
       }
    }

    private void processPV (Topology topology, NodeTemplate node, String qnameModule, RelationshipTemplate connect, NodeTemplate pv) {
       String pvName = node.getName() + pv.getName();
       log.debug ("Adding node {} named {}", K8S_TYPES_VOLUMES_CLAIM_SC, pvName);
       NodeTemplate pvc = addNodeTemplate(null, topology, pvName, K8S_TYPES_VOLUMES_CLAIM_SC, getK8SCsarVersion(topology));
       log.debug ("Setting name to {}", pvName.toLowerCase());
       setNodePropertyPathValue(null, topology, pvc, "name", new ScalarPropertyValue(pvName.toLowerCase())); 
       Capability endpoint = safe(pv.getCapabilities()).get("pvk8s_endpoint");
       log.debug ("Setting storageClassName to {}", endpoint.getProperties().get("storageClass"));
       setNodePropertyPathValue(null, topology, pvc, "storageClassName", endpoint.getProperties().get("storageClass")); 
       log.debug ("Setting accessModes to {}", endpoint.getProperties().get("accessModes"));
       setNodePropertyPathValue(null, topology, pvc, "accessModes", endpoint.getProperties().get("accessModes"));
       log.debug ("Setting size to {}", endpoint.getProperties().get("storage"));
       setNodePropertyPathValue(null, topology, pvc, "size", endpoint.getProperties().get("storage"));

       Map<String, Object> matchLabels = new HashMap<String, Object>();
       matchLabels.put("qname", endpoint.getProperties().get("qnamePV"));
       Map<String, Object> selector = new HashMap<String, Object>();
       selector.put("matchLabels", matchLabels);
       log.debug ("Setting selector to {}", selector);
       setNodePropertyPathValue(null, topology, pvc, "selector", new ComplexPropertyValue(selector));
        

       NodeTemplate deploy = TopologyNavigationUtil.getImmediateHostTemplate (topology, node);
       log.debug ("Adding {} relation to {}", NormativeRelationshipConstants.HOSTED_ON, deploy.getName());
       addRelationshipTemplate (null, topology, pvc, deploy.getName(), NormativeRelationshipConstants.HOSTED_ON,
                                "host", "host");
       log.debug ("Adding org.alien4cloud.relationships.MountDockerVolume relation to {}", node.getName());
       RelationshipTemplate attach = addRelationshipTemplate (null, topology, pvc, node.getName(), 
                                                              "org.alien4cloud.relationships.MountDockerVolume",
                                                              "attachment", "attach");
       log.debug ("Setting container_path to {}", connect.getProperties().get("mount_path"));
       attach.getProperties().put("container_path", connect.getProperties().get("mount_path"));

       log.debug ("Adding node {}:{} named {}", configuration.getPvNodeType(), configuration.getPvVersion(), pvName + "_PV");
       NodeTemplate pvw = addNodeTemplate(null, topology, pvName + "_PV", configuration.getPvNodeType(), configuration.getPvVersion());
       log.debug ("Setting qnameModule to {}", qnameModule);
       setNodePropertyPathValue(null, topology, pvw, "qnameModule", new ScalarPropertyValue(qnameModule)); 
       log.debug ("Setting qnamePV to {}", endpoint.getProperties().get("qnamePV"));
       setNodePropertyPathValue(null, topology, pvw, "qnamePV", endpoint.getProperties().get("qnamePV")); 
       
    }
}

