package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.common.MetaPropertiesService;
import alien4cloud.model.common.Tag;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.deployment.DeploymentService;
import alien4cloud.events.DeploymentCreatedEvent;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.orchestrators.locations.services.LocationService;
import alien4cloud.paas.IPaasEventListener;
import alien4cloud.paas.IPaasEventService;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.tosca.context.ToscaContext;
import static alien4cloud.utils.AlienUtils.safe;

import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;

import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.model.Application;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.Pds;
import org.alien4cloud.plugin.datagouv_mls.utils.ProcessLauncher;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("datagouv_mls-listener")
public class DatagouvMLSListener implements ApplicationListener<DeploymentCreatedEvent> {

    @Resource
    private MetaPropertiesService metaPropertiesService;

    @Inject
    private ApplicationEnvironmentService environmentService;

    @Inject
    private IPaasEventService eventService;

    @Inject
    private DeploymentService deploymentService;

    @Inject
    private DeploymentRuntimeStateService deploymentRuntimeStateService;

    @Inject
    private LocationService locationService;

    @Resource
    private DatagouvMLSConfiguration configuration;

    private Map<String,Application> applis = new HashMap<String,Application>();
    private List<String> exemptedApplis = new ArrayList<String>();

    public void storeAppli (String name, Application appli) {
       synchronized(this) {
          applis.put (name, appli);
       }
    }

    public void storeExemptedAppli (String name) {
       synchronized(this) {
          exemptedApplis.add (name);
       }
    }

    private boolean isExempted(String appli) {
       return exemptedApplis.contains(appli);
    }

    @PostConstruct
    public void init() {
        eventService.addListener(listener);
    }

    @PreDestroy
    public void term() {
        eventService.removeListener(listener);
    }

    IPaasEventListener listener = new IPaasEventListener() {
        @Override
        public void eventHappened(AbstractMonitorEvent event) {
             handleEvent((PaaSDeploymentStatusMonitorEvent) event);
        }

        @Override
        public boolean canHandle(AbstractMonitorEvent event) {
            return (event instanceof PaaSDeploymentStatusMonitorEvent);
        }
    };

    @Override
    public void onApplicationEvent(DeploymentCreatedEvent inputEvent) {
        Deployment deployment = deploymentService.get(inputEvent.getDeploymentId());
        processPreDeployment (deployment);
    }

    private void handleEvent(PaaSDeploymentStatusMonitorEvent inputEvent) {
        Deployment deployment = deploymentService.get(inputEvent.getDeploymentId());

        switch(inputEvent.getDeploymentStatus()) {
            case DEPLOYED:
                processPostDeployment (deployment);
                break;
            case UNDEPLOYED:
                processUnDeployment (deployment);
                break;
            default:
                return;
        }
    }

    private void processPreDeployment (Deployment deployment) {
       log.info ("Processing pre-deployment " + deployment.getId());

       ApplicationEnvironment env = environmentService.getOrFail(deployment.getEnvironmentId());
       if (isExempted(deployment.getSourceName() + "-" + env.getName()) ||
           !isModifierActive(deployment)) {
          log.info ("application not processed by datagouv_mls plugin");
          return;
       }

       /* send request to getPds : a PDS has to be set */
       String errmsg = null;
       try {
          Application fullAppli = applis.get(deployment.getSourceName() + "-" + env.getName());
          String qname = fullAppli.getEntities().get(0).getAttributes().getQualifiedName();

          String[] commands = new String[6];
          commands = new String[5];
          commands[0] = "curl";
          commands[1] = "-k";
          commands[2] = "-u";
          commands[3] = configuration.getGetPdsCredentials();
          commands[4] = configuration.getGetPdsUrl() + URLEncoder.encode(qname, StandardCharsets.UTF_8.toString());
          StringBuffer output = new StringBuffer();
          StringBuffer error = new StringBuffer();

          int ret = ProcessLauncher.launch(commands, output, error);
          if (ret != 0) {
             log.error ("Error " + ret +"[" + error.toString() + "]");
             errmsg = error.toString();
          } else {
             log.debug ("GET PDS RESPONSE=" + output.toString());
             Pds pds = (new ObjectMapper()).readValue(output.toString(), Pds.class);

             if ((pds.getErreurZone() != null) && !pds.getErreurZone().trim().equals("")) { 
                log.error ("DataGouv GetPds error: " + pds.getErreurZone());
                errmsg = pds.getErreurZone();
            } else if ((pds.getZone() == null) || pds.getZone().trim().equals("")) {
                log.error ("DataGouv GetPds response contains no zone!");
                errmsg = "GetPds response contains no zone!";
                if (pds.getMessage() != null) {
                   log.error("DataGouv GetPds error: " + pds.getMessage() + " [" + pds.getCode() + "]");
                   errmsg = pds.getMessage();
                }
            }
          }
       } catch (Exception e) {
          log.error ("Got exception:" + e.getMessage());
       }
       if (errmsg != null) { // no PDS: can not deploy
          throw new PaaSDeploymentException(errmsg);
       }
    }

    private void processPostDeployment (Deployment deployment) {
       log.info ("Processing post-deployment " + deployment.getId());

       ApplicationEnvironment env = environmentService.getOrFail(deployment.getEnvironmentId());
       if (isExempted(deployment.getSourceName() + "-" + env.getName()) ||
           !isModifierActive(deployment)) {
          log.info ("application not processed by datagouv_mls plugin");
          return;
       }

       try {
          Application fullAppli = applis.get(deployment.getSourceName() + "-" + env.getName());
          fullAppli.getEntities().get(0).getAttributes().setStatus("VALIDATED");
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
             log.error ("Error " + ret +"[" + error.toString() + "]");
          } else {
             log.debug ("POST RESPONSE=" + output.toString());
             Application retAppli = (new ObjectMapper()).readValue(output.toString(), Application.class);

             if ((retAppli.getEntities() == null) || (retAppli.getEntities().size() == 0)) {
                 log.error("DataGouv response contains no entity !");
                 if (retAppli.getMessage() != null) {
                    log.error("Error while updating application: " + retAppli.getMessage() + " [" + retAppli.getCode() + "]");
                 }
             }
          }

       } catch (Exception e) {
          log.error ("Got exception:", e);
       }

    }

    private int guid = -1;

    private String getGuid() {
       String ret = String.valueOf(guid);
       guid--;
       return ret;
    }

    private void processUnDeployment (Deployment deployment) {
       log.info ("Processing undeployment " + deployment.getId());

       ApplicationEnvironment env = environmentService.getOrFail(deployment.getEnvironmentId());
       String appVersion = deployment.getVersionId();
       String appliName = deployment.getSourceName() + "-" + env.getName();
       if (isExempted(appliName)) {
          log.info ("application not processed by datagouv_mls plugin");
          exemptedApplis.remove(appliName);
          return;
       }
       applis.remove(appliName);

       Topology topology = deploymentRuntimeStateService.getUnprocessedTopology(deployment.getId());
       String appQualifiedName = null;
       List<Tag> topoTags = topology.getTags();
       for (Tag tag: safe(topoTags)) {
          if (tag.getName().equals("qualifiedName")) {
             appQualifiedName = tag.getValue();
          }
       }
       if (appQualifiedName == null) {
          log.warn ("Cannot find app qualified name, cannot process undeployment");
          return;
       }
       
       String startTime = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(deployment.getStartDate()).toString();
       String endTime = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(deployment.getEndDate()).toString();

       /* whole application */
       Application fullAppli = new Application();
       List<Entity> entities = new ArrayList<Entity>();
       Map<String,Entity> referredEntities = new HashMap<String,Entity>();
       fullAppli.setEntities(entities);
       fullAppli.setReferredEntities(referredEntities);

       /* entity describing the application */
       Entity appli = new Entity();
       guid = -1;
       String appliId = getGuid();
       appli.setTypeName (DatagouvMLSConstants.APPLI_NAME);
       appli.setGuid (appliId);
       Attributes attribs = new Attributes();
       attribs.setName(deployment.getSourceName());
       attribs.setQualifiedName(appQualifiedName);
       attribs.setVersion(appVersion);
       attribs.setStartTime(startTime);
       attribs.setEndTime(endTime);
       attribs.setStatus("VALIDATED");
       appli.setAttributes(attribs);

       entities.add(appli);

       ToscaContext.init(topology.getDependencies());
       /* process nodes */
       Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
       /* list of nodes names */
       List<String> nodes = new ArrayList<String>();
       boolean hasModule = false;
       for (String nodeName : nodeTemplates.keySet()) {
          NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplates.get(nodeName).getType());
          String typeCompo = getMetaprop(nodeType, DatagouvMLSConstants.COMPONENT_TYPE);
          if ((typeCompo!=null) && typeCompo.equalsIgnoreCase("Module")) {
             log.info ("Processing node " + nodeName);
             hasModule = true;
             String version = nodeType.getArchiveVersion();

             String moduleGuid = getGuid();

             /* module entity descriptor */
             Entity module = new Entity();
             module.setTypeName(DatagouvMLSConstants.MODULE_INSTANCE_NAME);
             attribs = new Attributes();
             attribs.setName(nodeName);
             attribs.setQualifiedName(appQualifiedName + "-" + nodeName);
             nodes.add(nodeName + "-" + appliName);
             attribs.setStartTime(startTime);
             attribs.setEndTime(endTime);
             attribs.setVersion(appVersion);
             Entity instance = new Entity();
             instance.setTypeName(DatagouvMLSConstants.MODULE_NAME);
             instance.setGuid(moduleGuid);
             attribs.setInstanceOf(instance);
             Entity member = new Entity();
             member.setTypeName(DatagouvMLSConstants.APPLI_NAME);
             member.setGuid(appliId);
             attribs.setMemberOf(member);
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
       ToscaContext.destroy();

       if (!hasModule) {
          log.info("No modules, nothing to do.");
          return;
       }

       try {
          /* post JSON to update appli */
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
             log.error ("Error " + ret +"[" + error.toString() + "]");
          } else {
             log.debug ("POST RESPONSE=" + output.toString());
             Application retAppli = (new ObjectMapper()).readValue(output.toString(), Application.class);
   
             if ((retAppli.getEntities() == null) || (retAppli.getEntities().size() == 0)) {
                 log.error("DataGouv response contains no entity !");
                 if (retAppli.getMessage() != null) {
                    log.error("Error while updating application: " + retAppli.getMessage());
                 }
             }
          }

          /* send request to delete appli */
          commands = new String[7];
          commands[0] = "curl";
          commands[1] = "-k";
          commands[2] = "-X";
          commands[3] = "DELETE";
          commands[4] = "-u";
          commands[5] = configuration.getApplicationDeleteCredentials();
          commands[6] = configuration.getApplicationDeleteAppliUrl() + URLEncoder.encode(appQualifiedName, StandardCharsets.UTF_8.toString());
          output = new StringBuffer();
          error = new StringBuffer();

          log.debug("APPLI DEL URL=" + configuration.getApplicationDeleteAppliUrl() + URLEncoder.encode(appQualifiedName, StandardCharsets.UTF_8.toString()));

          ret = ProcessLauncher.launch(commands, output, error);
          if (ret != 0) {
             log.error ("Error " + ret +"[" + error.toString() + "]");
          } else {
             log.debug ("APPLI DEL RESPONSE=" + output.toString());
          }
          if (output.length() == 0) {
              log.error("Can not delete application from Atlas");
          }

          /* send requests to delete modules */
          for (Entity entity : fullAppli.getEntities()) {
             if (entity.getTypeName().equals(DatagouvMLSConstants.MODULE_INSTANCE_NAME)) {
                commands = new String[7];
                commands[0] = "curl";
                commands[1] = "-k";
                commands[2] = "-X";
                commands[3] = "DELETE";
                commands[4] = "-u";
                commands[5] = configuration.getApplicationDeleteCredentials();
                commands[6] = configuration.getApplicationDeleteModuleUrl() + 
                                           URLEncoder.encode(entity.getAttributes().getQualifiedName(), 
                                           StandardCharsets.UTF_8.toString());
                output = new StringBuffer();
                error = new StringBuffer();

                log.debug ("MODULE DEL URL=" + configuration.getApplicationDeleteModuleUrl() + 
                                           URLEncoder.encode(entity.getAttributes().getQualifiedName(), 
                                           StandardCharsets.UTF_8.toString()));

                ret = ProcessLauncher.launch(commands, output, error);
                if (ret != 0) {
                   log.error ("Error " + ret +"[" + error.toString() + "]");
                } else {
                   log.debug ("MODULE DEL RESPONSE=" + output.toString());
                   if (output.length() == 0) {
                      log.error("Can not delete module from Atlas");
                   }
                }
             }
          }


       } catch (Exception e) {
            log.error ("Got exception:", e);
       }
    }

    private String getMetaprop (NodeType node, String prop) {
       String propKey = metaPropertiesService.getMetapropertykeyByName(prop, MetaPropertyTarget.COMPONENT);

       if (propKey != null) {
          return safe(node.getMetaProperties()).get(propKey);
       }

       return null;
    }

    private boolean isModifierActive (Deployment deployment) {
       for (Location location : locationService.getMultiple (Arrays.asList (deployment.getLocationIds())).values()) {
          long count = safe(location.getModifiers()).stream().
                              filter(modifier->modifier.getBeanName().equals("datagouv_mls-modifier")).count();
          if (count > 0) {
             return true;
          }
       }
       return false;
    }

}
