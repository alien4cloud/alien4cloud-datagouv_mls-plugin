package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.deployment.DeploymentService;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.IPaasEventListener;
import alien4cloud.paas.IPaasEventService;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.tosca.context.ToscaContext;
import static alien4cloud.utils.AlienUtils.safe;

import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;

import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.utils.ProcessLauncher;
import org.alien4cloud.plugin.datagouv_mls.model.Application;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("datagouv_mls-listener")
public class DatagouvMLSListener {

    @Inject
    private ApplicationEnvironmentService environmentService;

    @Inject
    private IPaasEventService eventService;

    @Inject
    private DeploymentService deploymentService;

    @Inject
    private DeploymentRuntimeStateService deploymentRuntimeStateService;

    @Resource
    private DatagouvMLSConfiguration configuration;

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

    private void handleEvent(PaaSDeploymentStatusMonitorEvent inputEvent) {
        Deployment deployment = deploymentService.get(inputEvent.getDeploymentId());

        switch(inputEvent.getDeploymentStatus()) {
            case DEPLOYED:
                processDeployment (deployment);
                break;
            case UNDEPLOYED:
                processUnDeployment (deployment);
                break;
            default:
                return;
        }
    }

    private void processDeployment (Deployment deployment) {
       log.info ("Processing deployment " + deployment.getId());

       ApplicationEnvironment env = environmentService.getOrFail(deployment.getEnvironmentId());

       Application fullAppli = new Application();
       Entity appli = new Entity();
       appli.setTypeName (DatagouvMLSConstants.APPLI_NAME);
       appli.setGuid ("-1");
       Attributes attribs = new Attributes();
       attribs.setName(deployment.getSourceName());
       attribs.setQualifiedName(deployment.getSourceName() + "-" + env.getName());
       attribs.setVersion(deployment.getVersionId());
       attribs.setStartTime((new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(deployment.getStartDate()).toString());
       attribs.setStatus("VALIDATED");
       appli.setAttributes(attribs);

       List<Entity> entities = new ArrayList<Entity>();
       entities.add(appli);
       fullAppli.setEntities(entities);

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
             log.error ("Error " + ret +"[" + error.toString() + "]");
          } else {
             log.debug ("POST RESPONSE=" + output.toString());
          }
       } catch (Exception e) {
          log.error ("Got exception:" + e.getMessage());
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
       String appliName = deployment.getSourceName() + "-" + env.getName();
       appli.setTypeName (DatagouvMLSConstants.APPLI_NAME);
       appli.setGuid (appliId);
       Attributes attribs = new Attributes();
       attribs.setName(deployment.getSourceName());
       attribs.setQualifiedName(appliName);
       attribs.setVersion(appVersion);
       attribs.setStartTime(startTime);
       attribs.setEndTime(endTime);
       appli.setAttributes(attribs);

       entities.add(appli);

       Topology topology = deploymentRuntimeStateService.getUnprocessedTopology(deployment.getId());
       ToscaContext.init(topology.getDependencies());
       /* process nodes */
       Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
       /* list of nodes names */
       List<String> nodes = new ArrayList<String>();
       for (String nodeName : nodeTemplates.keySet()) {
          /* nodes to be added contain a "container" property */
          if ( safe(nodeTemplates.get(nodeName).getProperties()).get("container") != null) {
             log.info ("Processing node " + nodeName);
             NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplates.get(nodeName).getType());
             String version = nodeType.getArchiveVersion();

             String moduleGuid = getGuid();

             /* module entity descriptor */
             Entity module = new Entity();
             module.setTypeName(DatagouvMLSConstants.MODULE_INSTANCE_NAME);
             attribs = new Attributes();
             attribs.setName(nodeName);
             attribs.setQualifiedName(nodeName + "-" + appliName);
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
          }

          /* send request to delete appli */
          commands = new String[7];
          commands[0] = "curl";
          commands[1] = "-k";
          commands[2] = "-X";
          commands[3] = "DELETE";
          commands[4] = "-u";
          commands[5] = configuration.getApplicationDeleteCredentials();
          commands[6] = configuration.getApplicationDeleteAppliUrl() + URLEncoder.encode(appliName, StandardCharsets.UTF_8.toString());
          output = new StringBuffer();
          error = new StringBuffer();

          ret = ProcessLauncher.launch(commands, output, error);
          if (ret != 0) {
             log.error ("Error " + ret +"[" + error.toString() + "]");
          } else {
             log.debug ("APPLI DEL RESPONSE=" + output.toString());
          }

          /* send requests to delete modules */
          for (String module : nodes) {
             commands = new String[7];
             commands[0] = "curl";
             commands[1] = "-k";
             commands[2] = "-X";
             commands[3] = "DELETE";
             commands[4] = "-u";
             commands[5] = configuration.getApplicationDeleteCredentials();
             commands[6] = configuration.getApplicationDeleteModuleUrl() + URLEncoder.encode(module, StandardCharsets.UTF_8.toString());
             output = new StringBuffer();
             error = new StringBuffer();

             ret = ProcessLauncher.launch(commands, output, error);
             if (ret != 0) {
                log.error ("Error " + ret +"[" + error.toString() + "]");
             } else {
                log.debug ("MODULE DEL RESPONSE=" + output.toString());
             }
          }


       } catch (Exception e) {
            log.error ("Got exception:" + e.getMessage());
       }
    }
}