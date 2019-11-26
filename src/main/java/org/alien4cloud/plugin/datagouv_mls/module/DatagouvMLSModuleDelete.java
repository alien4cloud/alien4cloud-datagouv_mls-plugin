package org.alien4cloud.plugin.datagouv_mls.module;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.model.common.MetaPropertyTarget;
import static alien4cloud.utils.AlienUtils.safe;
import org.alien4cloud.tosca.catalog.events.BeforeArchiveDeleted;
import org.alien4cloud.tosca.catalog.index.IToscaTypeIndexerService;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.utils.ProcessLauncher;
import org.alien4cloud.plugin.datagouv_mls.utils.TopologyUtils;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.Map;

@Slf4j
@Component("datagouv_mls-module-delete")
public class DatagouvMLSModuleDelete implements ApplicationListener<BeforeArchiveDeleted> {

    @Resource
    private DatagouvMLSConfiguration configuration;

    @Resource
    private MetaPropertiesService metaPropertiesService;

    @Inject
    private IToscaTypeIndexerService indexerService;

    @Override
    public void onApplicationEvent(BeforeArchiveDeleted event) {
       try {
         int pos = event.getArchiveId().indexOf(":");
         String archiveName = event.getArchiveId().substring(0,pos);
         String archiveVersion = event.getArchiveId().substring(pos+1);

         /**
          * Get archive nodes if any:
          * archive must contain at least one node of type module
          **/
         Map<String,NodeType> nodeTypes = indexerService.getArchiveElements (archiveName, archiveVersion, NodeType.class);

         if (nodeTypes != null) {
            boolean keepIt = false;
            for (String nodename : nodeTypes.keySet()) {
               NodeType node = nodeTypes.get(nodename);
               String typeCompo = getMetaprop(node, DatagouvMLSConstants.COMPONENT_TYPE);
               if ((typeCompo != null) && typeCompo.equalsIgnoreCase("Module")) {
                  keepIt = true;
               }
            }

            if (keepIt) {
                log.info ("Processing " + archiveName);
                String[] commands = new String[7];
                commands[0] = "curl";
                commands[1] = "-k";
                commands[2] = "-X";
                commands[3] = "DELETE";
                commands[4] = "-u";
                commands[5] = configuration.getModuleDeleteCredentials();
                commands[6] = configuration.getModuleDeleteUrl() + archiveName;
                StringBuffer output = new StringBuffer();
                StringBuffer error = new StringBuffer();

                int ret = ProcessLauncher.launch(commands, output, error);

                if (ret != 0) {
                   log.error ("Error " + ret +"[" + error.toString() + "]");
                }
            }
         }

       } catch (Exception e) {
         log.error ("Got exception: " + e.getMessage());
       }
    }

    private String getMetaprop (NodeType node, String prop) {
       String propKey = metaPropertiesService.getMetapropertykeyByName(prop, MetaPropertyTarget.COMPONENT);

       if (propKey != null) {
          return safe(node.getMetaProperties()).get(propKey);
       }

       return null;
    }

}
