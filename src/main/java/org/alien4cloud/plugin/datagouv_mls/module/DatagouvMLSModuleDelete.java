package org.alien4cloud.plugin.datagouv_mls.module;

import alien4cloud.model.common.Tag;
import org.alien4cloud.tosca.catalog.events.BeforeArchiveDeleted;
import org.alien4cloud.tosca.catalog.index.IToscaTypeIndexerService;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.utils.ProcessLauncher;

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
               if (node.getTags() != null) {
                  for (Tag tag : node.getTags()) {
                     if (tag.getName().equals(DatagouvMLSConstants.COMPONENT_TYPE) && tag.getValue().equalsIgnoreCase("Module")) {
                       keepIt = true;
                     }
                  }
               }
            }

            if (keepIt) {
                log.info ("Processing " + archiveName);
                String[] commands = new String[6];
                commands[0] = "curl";
                commands[1] = "-X";
                commands[2] = "DELETE";
                commands[3] = "-u";
                commands[4] = configuration.getModuleDeleteCredentials();
                commands[5] = configuration.getModuleDeleteUrl() + archiveName;
                StringBuffer output = new StringBuffer();

                int ret = ProcessLauncher.launch(commands, output);

                if (ret != 0) {
                   log.error ("Error " + ret +"[" + output.toString() + "]");
                }
            }
         }

       } catch (Exception e) {
         log.error ("Got exception: " + e.getMessage());
       }
    }
}
