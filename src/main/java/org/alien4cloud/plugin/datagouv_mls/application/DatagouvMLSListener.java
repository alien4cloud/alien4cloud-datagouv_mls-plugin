package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.deployment.DeploymentService;
import alien4cloud.events.DeploymentCreatedEvent;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.IPaasEventListener;
import alien4cloud.paas.IPaasEventService;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;

import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.model.Application;
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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("datagouv_mls-listener")
public class DatagouvMLSListener implements ApplicationListener<DeploymentCreatedEvent> {

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

    private Map<String,Application> applis = new HashMap<String,Application>();

    public void storeAppli (String name, Application appli) {
       synchronized(this) {
          applis.put (name, appli);
       }
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

       /* send request to getPds : a PDS has to be set */
       String errmsg = null;
       try {
          String[] commands = new String[6];
          commands = new String[5];
          commands[0] = "curl";
          commands[1] = "-k";
          commands[2] = "-u";
          commands[3] = configuration.getGetPdsCredentials();
          commands[4] = configuration.getGetPdsUrl() + URLEncoder.encode(deployment.getSourceName() + "-" + env.getName(), StandardCharsets.UTF_8.toString());
          StringBuffer output = new StringBuffer();
          StringBuffer error = new StringBuffer();

          int ret = ProcessLauncher.launch(commands, output, error);
          if (ret != 0) {
             log.error ("Error " + ret +"[" + error.toString() + "]");
             errmsg = error.toString();
          } else {
             log.debug ("GET PDS RESPONSE=" + output.toString());
             Pds pds = (new ObjectMapper()).readValue(output.toString(), Pds.class);

             if ((pds.getErreur() != null) && !pds.getErreur().trim().equals("")) { 
                log.error ("DataGouv GetPds error: " + pds.getErreur());
                errmsg = pds.getErreur();
            } else if ((pds.getZone() == null) || pds.getZone().trim().equals("")) {
                if ((pds.getLocalizedMessage() != null) && !pds.getLocalizedMessage().trim().equals("")) { 
                   log.error ("DataGouv GetPds error: " + pds.getLocalizedMessage());
                   errmsg = pds.getLocalizedMessage();
                } else {
                   log.error ("DataGouv GetPds response contains no zone!");
                   errmsg = "GetPds response contains no zone!";
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
       
       String endTime = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(deployment.getEndDate()).toString();

       Application fullAppli = applis.get(appliName);
       for (Entity entity : fullAppli.getEntities()) {
          entity.getAttributes().setEndTime (endTime);
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

                ret = ProcessLauncher.launch(commands, output, error);
                if (ret != 0) {
                   log.error ("Error " + ret +"[" + error.toString() + "]");
                } else {
                   log.debug ("MODULE DEL RESPONSE=" + output.toString());
                }
             }
          }


       } catch (Exception e) {
            log.error ("Got exception:", e);
       }
    }
}
